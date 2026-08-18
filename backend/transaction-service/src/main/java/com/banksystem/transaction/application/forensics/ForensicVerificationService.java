package com.banksystem.transaction.application.forensics;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.api.dto.ForensicDtos.FinancialViolationResponse;
import com.banksystem.transaction.api.dto.ForensicDtos.InvestigationDetailResponse;
import com.banksystem.transaction.api.dto.ForensicVerificationDtos.VerificationRuleResultResponse;
import com.banksystem.transaction.api.dto.ForensicVerificationDtos.VerificationRunResponse;
import com.banksystem.transaction.domain.audit.AuditLogEntity;
import com.banksystem.transaction.domain.audit.AuditLogRepository;
import com.banksystem.transaction.domain.forensics.ForensicVerificationResultEntity;
import com.banksystem.transaction.domain.forensics.ForensicVerificationResultRepository;
import com.banksystem.transaction.domain.forensics.ForensicVerificationRunEntity;
import com.banksystem.transaction.domain.forensics.ForensicVerificationRunRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ForensicVerificationService {
  private static final Logger log = LoggerFactory.getLogger(ForensicVerificationService.class);
  private final ForensicVerificationRunRepository runRepository;
  private final ForensicVerificationResultRepository resultRepository;
  private final ForensicInvestigationQueryService investigationQueryService;
  private final AuditLogRepository auditRepository;
  private final ForensicJsonSupport jsonSupport;
  private final ForensicsFeatureGate featureGate;
  private final Clock clock;
  private final ForensicTelemetry telemetry;
  private final ForensicCaseOrchestratorService orchestratorService;

  public ForensicVerificationService(
      ForensicVerificationRunRepository runRepository,
      ForensicVerificationResultRepository resultRepository,
      ForensicInvestigationQueryService investigationQueryService,
      AuditLogRepository auditRepository,
      ForensicJsonSupport jsonSupport,
      ForensicsFeatureGate featureGate,
      Clock clock,
      ForensicTelemetry telemetry,
      ForensicCaseOrchestratorService orchestratorService) {
    this.runRepository = runRepository;
    this.resultRepository = resultRepository;
    this.investigationQueryService = investigationQueryService;
    this.auditRepository = auditRepository;
    this.jsonSupport = jsonSupport;
    this.featureGate = featureGate;
    this.clock = clock;
    this.telemetry = telemetry;
    this.orchestratorService = orchestratorService;
  }

  @Transactional
  public VerificationRunResponse check(UUID transactionId, UUID actor, String idempotencyKey) {
    long startedNanos = System.nanoTime();
    featureGate.requireEnabled();
    String normalizedKey = requireIdempotencyKey(idempotencyKey);
    ForensicVerificationRunEntity existing = runRepository
        .findByRequestedByAndIdempotencyKey(actor, normalizedKey).orElse(null);
    if (existing != null) {
      if (!existing.getTransactionId().equals(transactionId)) {
        throw new BusinessException(
            "FORENSIC_IDEMPOTENCY_CONFLICT",
            "Idempotency key was already used for another transaction");
      }
      return response(existing);
    }

    Instant startedAt = clock.instant();
    ForensicVerificationRunEntity run = runRepository.saveAndFlush(
        ForensicVerificationRunEntity.running(
            UUID.randomUUID(), transactionId, actor, normalizedKey, startedAt));
    InvestigationDetailResponse investigation = investigationQueryService.get(transactionId);
    List<ForensicVerificationResultEntity> results = investigation.violations().stream()
        .map(violation -> toResult(run.getId(), violation, startedAt))
        .toList();
    results.forEach(result -> resultRepository.insert(
        result.getId(), run.getId(), result.getRuleCode(), result.getOutcome(),
        result.getSeverity(), result.getMessage(), result.getEvidenceJson(),
        result.getEvaluatedAt()));
    String outcome = results.isEmpty() ? "PASS" : "FAIL";
    run.complete(outcome, investigation.transaction().updatedAt(), clock.instant());
    runRepository.save(run);

    if ("FAIL".equals(outcome)) {
      try {
        orchestratorService.orchestrateForTransaction(transactionId, actor);
      } catch (Exception e) {
        log.error("Failed to auto-orchestrate forensic case for transaction {}: {}", transactionId, e.getMessage(), e);
      }
    }

    auditRepository.save(AuditLogEntity.of(
        actor, "FORENSIC_VERIFICATION_RUN", "FORENSIC_VERIFICATION",
        run.getId().toString(), null,
        jsonSupport.serialize(Map.of(
            "transactionId", transactionId.toString(),
            "outcome", outcome,
            "resultCount", results.size()))));
    VerificationRunResponse response = response(run, results);
    telemetry.verification(normalizedKey.startsWith("batch:") ? "BATCH" : "ON_DEMAND",
        outcome, System.nanoTime() - startedNanos);
    return response;
  }

  @Transactional(readOnly = true)
  public VerificationRunResponse get(UUID runId) {
    featureGate.requireEnabled();
    ForensicVerificationRunEntity run = runRepository.findById(runId)
        .orElseThrow(() -> new BusinessException(
            "FORENSIC_VERIFICATION_RUN_NOT_FOUND", "Verification run not found"));
    return response(run);
  }

  private ForensicVerificationResultEntity toResult(
      UUID runId, FinancialViolationResponse violation, Instant evaluatedAt) {
    return ForensicVerificationResultEntity.of(
        runId,
        violation.ruleCode(),
        "FAIL",
        violation.severity(),
        violation.message() == null ? violation.ruleCode() : violation.message(),
        jsonSupport.serialize(Map.of("evidenceIds", violation.evidenceIds())),
        evaluatedAt);
  }

  private VerificationRunResponse response(ForensicVerificationRunEntity run) {
    return response(run, resultRepository.findByRunIdOrderByRuleCode(run.getId()));
  }

  private VerificationRunResponse response(
      ForensicVerificationRunEntity run, List<ForensicVerificationResultEntity> results) {
    return new VerificationRunResponse(
        run.getId().toString(), run.getTransactionId().toString(), run.getRuleSetVersion(),
        run.getStatus(), run.getOutcome(), run.getSourceWatermark(), run.getStartedAt(),
        run.getCompletedAt(), results.stream().map(this::toResponse).toList());
  }

  private VerificationRuleResultResponse toResponse(ForensicVerificationResultEntity result) {
    return new VerificationRuleResultResponse(
        result.getId().toString(), result.getRuleCode(), result.getOutcome(), result.getSeverity(),
        result.getMessage(), jsonSupport.deserialize(result.getEvidenceJson()),
        result.getEvaluatedAt());
  }

  private String requireIdempotencyKey(String value) {
    if (value == null || value.isBlank() || value.length() > 120) {
      throw new BusinessException(
          "FORENSIC_IDEMPOTENCY_KEY_REQUIRED",
          "Idempotency-Key is required and must not exceed 120 characters");
    }
    return value.trim();
  }
}
