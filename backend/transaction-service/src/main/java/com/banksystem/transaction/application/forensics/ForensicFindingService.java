package com.banksystem.transaction.application.forensics;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.common.api.PageResponse;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.CreateForensicFindingRequest;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.ForensicFindingResponse;
import com.banksystem.transaction.domain.audit.AuditLogEntity;
import com.banksystem.transaction.domain.audit.AuditLogRepository;
import com.banksystem.transaction.domain.forensics.ForensicFindingEntity;
import com.banksystem.transaction.domain.forensics.ForensicFindingRepository;
import com.banksystem.transaction.domain.outbox.OutboxEventRepository;
import com.banksystem.transaction.domain.reconciliation.ReconItemEntity;
import com.banksystem.transaction.domain.reconciliation.ReconItemRepository;
import com.banksystem.transaction.domain.transfer.SagaStepLogRepository;
import com.banksystem.transaction.domain.transfer.TransferOrderEntity;
import com.banksystem.transaction.domain.transfer.TransferOrderRepository;
import com.banksystem.transaction.domain.transfer.TransferStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@Service
public class ForensicFindingService {
  private static final Set<String> SEVERITIES =
      Set.of("INFO", "LOW", "MEDIUM", "HIGH", "CRITICAL");

  private final ForensicFindingRepository findingRepository;
  private final TransferOrderRepository transferRepository;
  private final SagaStepLogRepository sagaRepository;
  private final OutboxEventRepository outboxRepository;
  private final ReconItemRepository reconciliationRepository;
  private final ForensicJsonSupport jsonSupport;
  private final Clock clock;
  private final AuditLogRepository auditRepository;

  public ForensicFindingService(
      ForensicFindingRepository findingRepository,
      TransferOrderRepository transferRepository,
      SagaStepLogRepository sagaRepository,
      OutboxEventRepository outboxRepository,
      ReconItemRepository reconciliationRepository,
      ForensicJsonSupport jsonSupport,
      Clock clock,
      AuditLogRepository auditRepository) {
    this.findingRepository = findingRepository;
    this.transferRepository = transferRepository;
    this.sagaRepository = sagaRepository;
    this.outboxRepository = outboxRepository;
    this.reconciliationRepository = reconciliationRepository;
    this.jsonSupport = jsonSupport;
    this.clock = clock;
    this.auditRepository = auditRepository;
  }

  @Transactional
  public List<ForensicFindingResponse> synchronize(UUID transactionId, UUID caseId) {
    TransferOrderEntity transfer = transferRepository.findById(transactionId)
        .orElseThrow(() -> new BusinessException(
            "FORENSIC_TRANSACTION_NOT_FOUND", "Transaction investigation not found"));
    List<FindingCandidate> candidates = candidates(transfer);
    candidates.forEach(candidate -> persist(candidate, transactionId, caseId));
    return findingRepository.findByTransactionIdOrderByDetectedAtDesc(transactionId)
        .stream().map(this::toResponse).toList();
  }

  @Transactional
  public ForensicFindingResponse createManual(
      UUID caseId, UUID transactionId, CreateForensicFindingRequest request) {
    if (transactionId == null) {
      throw new BusinessException(
          "FORENSIC_FINDING_SUBJECT_REQUIRED", "Manual finding requires a transaction subject");
    }
    String severity = request.severity().trim().toUpperCase(Locale.ROOT);
    if (!SEVERITIES.contains(severity)) {
      throw new BusinessException("INVALID_FINDING_SEVERITY", "Unsupported finding severity");
    }
    String evidenceJson = jsonSupport.serialize(request.evidence());
    String evidenceHash = jsonSupport.sha256(evidenceJson);
    String ruleCode = request.ruleCode().trim().toUpperCase(Locale.ROOT);
    String key = "MANUAL:" + caseId + ":" + ruleCode + ":" + evidenceHash;
    persist(new FindingCandidate(
        key, ruleCode, "FAIL", severity, request.title().trim(), request.detail(),
        request.evidence() == null ? Map.of() : request.evidence()), transactionId, caseId);
    return findingRepository.findByFindingKey(key).map(this::toResponse)
        .orElseThrow(() -> new BusinessException("FORENSIC_FINDING_NOT_FOUND", "Finding not found"));
  }

  @Transactional(readOnly = true)
  public List<ForensicFindingResponse> listForCase(UUID caseId, UUID transactionId) {
    List<ForensicFindingEntity> findings = transactionId == null
        ? findingRepository.findByCaseIdOrderByDetectedAtDesc(caseId)
        : findingRepository.findByTransactionIdOrderByDetectedAtDesc(transactionId);
    return findings.stream().map(this::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public PageResponse<ForensicFindingResponse> searchViolations(
      String disposition, String severity, String ruleCode, UUID transactionId,
      Instant since, int page, int size) {
    Page<ForensicFindingEntity> result = findingRepository.searchViolations(
        disposition != null, normalize(disposition), severity != null, normalize(severity),
        ruleCode != null, normalize(ruleCode), transactionId != null,
        transactionId == null ? new UUID(0L, 0L) : transactionId,
        since == null ? Instant.EPOCH : since, PageRequest.of(page, size));
    return new PageResponse<>(result.getContent().stream().map(this::toResponse).toList(),
        result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
  }

  @Transactional(readOnly = true)
  public ForensicFindingResponse get(UUID id) { return toResponse(require(id)); }

  @Transactional
  public ForensicFindingResponse acknowledge(UUID id, UUID actor, long version, String note) {
    require(id);
    int updated = findingRepository.acknowledge(id, actor, "\nACK: " + note.trim(), clock.instant(), version);
    if (updated == 0) throw conflict();
    auditRepository.save(AuditLogEntity.of(actor, "FORENSIC_VIOLATION_ACKNOWLEDGED",
        "FORENSIC_VIOLATION", id.toString(), null, "version=" + version));
    return toResponse(require(id));
  }

  @Transactional
  public ForensicFindingResponse resolve(
      UUID id, UUID actor, long version, String reason, Map<String, Object> evidence) {
    ForensicFindingEntity finding = require(id);
    if ("CRITICAL".equals(finding.getSeverity()) && actor.equals(finding.getAcknowledgedBy())) {
      throw new BusinessException(
          "FORENSIC_MAKER_CHECKER_REQUIRED",
          "Critical violation must be resolved by a different user");
    }
    int updated = findingRepository.resolve(
        id, actor, reason.trim(), jsonSupport.serialize(evidence), clock.instant(), version);
    if (updated == 0) throw conflict();
    auditRepository.save(AuditLogEntity.of(actor, "FORENSIC_VIOLATION_RESOLVED",
        "FORENSIC_VIOLATION", id.toString(), null, "version=" + version));
    return toResponse(require(id));
  }

  private List<FindingCandidate> candidates(TransferOrderEntity transfer) {
    List<FindingCandidate> candidates = new ArrayList<>();
    UUID transactionId = transfer.getId();
    if (transfer.getStatus() == TransferStatus.UNKNOWN) {
      candidates.add(candidate("PROVIDER_STATUS_UNKNOWN", "CRITICAL", transfer,
          Map.of("providerStatus", safe(transfer.getProviderStatus()), "status", transfer.getStatus().name())));
    }
    if (transfer.getStatus() == TransferStatus.FAILED
        || transfer.getStatus() == TransferStatus.REVIEW_REQUIRED
        || transfer.getStatus() == TransferStatus.COMPENSATING) {
      candidates.add(candidate("TRANSFER_TERMINAL_ANOMALY", "HIGH", transfer,
          Map.of("status", transfer.getStatus().name(), "reason", safe(transfer.getFailureReason()))));
    }
    if (isOneOf(transfer.getRiskDecision(), "REVIEW", "BLOCK")) {
      candidates.add(candidate("RISK_DECISION", "BLOCK".equals(transfer.getRiskDecision()) ? "CRITICAL" : "HIGH",
          transfer, Map.of("decision", transfer.getRiskDecision(), "score",
              transfer.getRiskScore() == null ? 0 : transfer.getRiskScore())));
    }
    boolean sagaFailed = sagaRepository.findByTransferIdOrderByCreatedAtAsc(transactionId).stream()
        .anyMatch(step -> isOneOf(step.getStatus(), "FAILED", "ERROR"));
    if (sagaFailed) {
      candidates.add(candidate("SAGA_FAILED", "CRITICAL", transfer, Map.of("failed", true)));
    }
    boolean outboxDead = outboxRepository.findByAggregateIdOrderByCreatedAtAsc(transactionId).stream()
        .anyMatch(event -> "DEAD".equalsIgnoreCase(event.getStatus()));
    if (outboxDead) {
      candidates.add(candidate("OUTBOX_DEAD", "HIGH", transfer, Map.of("dead", true)));
    }
    List<ReconItemEntity> recon = reconciliationRepository.findByTransferIdOrderByKindAsc(transactionId);
    boolean mismatch = recon.stream().anyMatch(item -> item.getExpectedAmount() != null
        && item.getActualAmount() != null
        && item.getExpectedAmount().compareTo(item.getActualAmount()) != 0);
    if (mismatch) {
      candidates.add(candidate("RECONCILIATION_MISMATCH", "CRITICAL", transfer,
          Map.of("mismatchCount", recon.size())));
    }
    return candidates;
  }

  private FindingCandidate candidate(
      String rule, String severity, TransferOrderEntity transfer, Map<String, Object> evidence) {
    return new FindingCandidate(
        rule + ":" + transfer.getId(), rule, "FAIL", severity, rule,
        transfer.getFailureReason(), evidence);
  }

  private void persist(FindingCandidate candidate, UUID transactionId, UUID caseId) {
    String evidenceJson = jsonSupport.serialize(candidate.evidence());
    String evidenceHash = jsonSupport.sha256(evidenceJson);
    Instant now = clock.instant();
    int inserted = findingRepository.insertIfAbsent(
        UUID.randomUUID(), candidate.key(), caseId, transactionId, candidate.ruleCode(),
        transactionId.toString(), candidate.outcome(), candidate.severity(), candidate.title(),
        candidate.detail(), evidenceJson, evidenceHash, now);
    if (inserted == 0) {
      findingRepository.markSeen(candidate.key(), caseId, evidenceJson, evidenceHash, now);
    }
  }

  private ForensicFindingResponse toResponse(ForensicFindingEntity entity) {
    return new ForensicFindingResponse(
        entity.getId().toString(), entity.getFindingKey(), entity.getRuleCode(), entity.getOutcome(),
        entity.getSeverity(), entity.getDisposition(), entity.getTitle(), entity.getDetail(),
        jsonSupport.deserialize(entity.getEvidenceJson()), entity.getEvidenceHash(),
        entity.getOccurrenceCount(), entity.getDetectedAt(), entity.getLastSeenAt(),
        text(entity.getAcknowledgedBy()), entity.getAcknowledgedAt(), entity.getResolutionReason(),
        jsonSupport.deserialize(entity.getResolutionEvidence()), text(entity.getResolvedBy()),
        entity.getResolvedAt(), entity.getVersion());
  }

  private ForensicFindingEntity require(UUID id) {
    return findingRepository.findById(id).orElseThrow(() -> new BusinessException(
        "FORENSIC_VIOLATION_NOT_FOUND", "Forensic violation not found"));
  }

  private BusinessException conflict() {
    return new BusinessException(
        "FORENSIC_VIOLATION_CONFLICT", "Violation changed or is not in the required state");
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }

  private String text(UUID value) { return value == null ? null : value.toString(); }

  private boolean isOneOf(String value, String... expected) {
    if (value == null) return false;
    for (String candidate : expected) {
      if (candidate.equalsIgnoreCase(value)) return true;
    }
    return false;
  }

  private String safe(String value) { return value == null ? "" : value; }

  private record FindingCandidate(
      String key,
      String ruleCode,
      String outcome,
      String severity,
      String title,
      String detail,
      Map<String, Object> evidence) {}
}
