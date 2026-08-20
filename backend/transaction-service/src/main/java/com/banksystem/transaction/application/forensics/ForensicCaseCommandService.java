package com.banksystem.transaction.application.forensics;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.common.security.GatewayUser;
import com.banksystem.common.security.SecurityHeaders;
import com.banksystem.common.security.UserContext;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.ApproveForensicResolutionRequest;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.AssignForensicCaseRequest;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.ConfirmRootCauseRequest;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.CreateForensicCaseRequest;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.CreateForensicFindingRequest;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.ForensicCaseResponse;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.ForensicFindingResponse;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.RecordRemediationRequest;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.RejectForensicResolutionRequest;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.ReopenForensicCaseRequest;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.SubmitForensicCaseRequest;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.VerifyReplayRequest;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.VersionedForensicCaseRequest;
import com.banksystem.transaction.application.audit.AuditCommandService;
import com.banksystem.transaction.application.audit.AuditCommandService.CreateAuditLogCommand;
import com.banksystem.transaction.domain.audit.AuditLogRepository;
import com.banksystem.transaction.domain.forensics.EvidenceCompleteness;
import com.banksystem.transaction.domain.forensics.ForensicCaseEntity;
import com.banksystem.transaction.domain.forensics.ForensicCaseHistoryEntity;
import com.banksystem.transaction.domain.forensics.ForensicCaseHistoryRepository;
import com.banksystem.transaction.domain.forensics.ForensicCasePriority;
import com.banksystem.transaction.domain.forensics.ForensicCaseRepository;
import com.banksystem.transaction.domain.forensics.ForensicCaseStatus;
import com.banksystem.transaction.domain.forensics.ForensicResolutionCode;
import com.banksystem.transaction.domain.outbox.OutboxEventRepository;
import com.banksystem.transaction.domain.transfer.SagaStepLogRepository;
import com.banksystem.transaction.domain.transfer.TransferOrderRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ForensicCaseCommandService {
  private static final Set<String> SOURCE_TYPES =
      Set.of("MANUAL", "RISK", "RECONCILIATION", "INVARIANT", "SYSTEM_ALERT");
  private static final Set<ForensicCaseStatus> ACTIVE_STATUSES =
      Set.of(
          ForensicCaseStatus.OPEN,
          ForensicCaseStatus.ASSIGNED,
          ForensicCaseStatus.INVESTIGATING,
          ForensicCaseStatus.PENDING_CHECKER,
          ForensicCaseStatus.REOPENED);

  private final ForensicCaseRepository caseRepository;
  private final ForensicCaseHistoryRepository historyRepository;
  private final TransferOrderRepository transferRepository;
  private final SagaStepLogRepository sagaRepository;
  private final OutboxEventRepository outboxRepository;
  private final AuditLogRepository auditRepository;
  private final ForensicFindingService findingService;
  private final ForensicCaseMapper mapper;
  private final ForensicJsonSupport jsonSupport;
  private final AuditCommandService auditCommandService;
  private final ForensicCaseEventPublisher eventPublisher;
  private final ForensicScenarioService scenarioService;
  private final Clock clock;
  private final ForensicsFeatureGate featureGate;

  public ForensicCaseCommandService(
      ForensicCaseRepository caseRepository,
      ForensicCaseHistoryRepository historyRepository,
      TransferOrderRepository transferRepository,
      SagaStepLogRepository sagaRepository,
      OutboxEventRepository outboxRepository,
      AuditLogRepository auditRepository,
      ForensicFindingService findingService,
      ForensicCaseMapper mapper,
      ForensicJsonSupport jsonSupport,
      AuditCommandService auditCommandService,
      ForensicCaseEventPublisher eventPublisher,
      ForensicScenarioService scenarioService,
      Clock clock,
      ForensicsFeatureGate featureGate) {
    this.caseRepository = caseRepository;
    this.historyRepository = historyRepository;
    this.transferRepository = transferRepository;
    this.sagaRepository = sagaRepository;
    this.outboxRepository = outboxRepository;
    this.auditRepository = auditRepository;
    this.findingService = findingService;
    this.mapper = mapper;
    this.jsonSupport = jsonSupport;
    this.auditCommandService = auditCommandService;
    this.eventPublisher = eventPublisher;
    this.scenarioService = scenarioService;
    this.clock = clock;
    this.featureGate = featureGate;
  }


  @Transactional
  public ForensicCaseResponse create(CreateForensicCaseRequest request) {
    featureGate.requireEnabled();
    GatewayUser actor = UserContext.requireUser();
    validateSubject(request.transactionId(), request.accountId());
    String sourceType = normalizeSourceType(request.sourceType());
    String sourceReference = trim(request.sourceReferenceId());
    if (sourceReference != null) {
      ForensicCaseEntity existing = caseRepository
          .findBySourceTypeAndSourceReferenceId(sourceType, sourceReference).orElse(null);
      if (existing != null) {
        return mapper.toResponse(existing);
      }
    }
    if (request.transactionId() != null) {
      List<ForensicCaseEntity> activeCases = caseRepository
          .findByTransactionIdAndStatusInOrderByCreatedAtDesc(request.transactionId(), ACTIVE_STATUSES);
      if (!activeCases.isEmpty()) {
        return mapper.toResponse(activeCases.get(0));
      }
    }
    ForensicCasePriority priority = parsePriority(request.priority());
    Instant now = clock.instant();
    UUID id = UUID.randomUUID();
    ForensicCaseEntity entity = ForensicCaseEntity.create(
        id, caseNumber(id, now), request.transactionId(), request.accountId(), sourceType,
        sourceReference, priority, request.title().trim(), trim(request.summary()),
        actor.userId(), now);
    caseRepository.save(entity);
    if (request.transactionId() != null) {
      findingService.synchronize(request.transactionId(), id);
    }
    recordHistory(entity, actor.userId(), "CREATE", null, null, request.summary());
    recordAudit(entity, actor.userId(), "FORENSIC_CASE_CREATE", null);
    eventPublisher.publish("FORENSIC_CASE_CREATED", entity);
    return mapper.toResponse(entity);
  }

  @Transactional
  public ForensicCaseResponse assign(UUID id, AssignForensicCaseRequest request) {
    featureGate.requireEnabled();
    GatewayUser actor = UserContext.requireUser();
    if (!request.assignee().equals(actor.userId())
        && !actor.hasPermission(SecurityHeaders.PERM_FORENSICS_ADMIN)) {
      throw new BusinessException(
          "FORENSIC_CASE_FORBIDDEN", "Only forensic administrators can assign another user",
          HttpStatus.FORBIDDEN);
    }
    ForensicCaseEntity entity = requireVersion(id, request.expectedVersion());
    String from = entity.getStatus().name();
    entity.assign(request.assignee(), clock.instant());
    persistTransition(entity, actor.userId(), "ASSIGN", from, null, request.note(),
        "FORENSIC_CASE_ASSIGNED");
    return mapper.toResponse(entity);
  }

  @Transactional
  public ForensicCaseResponse start(UUID id, VersionedForensicCaseRequest request) {
    featureGate.requireEnabled();
    GatewayUser actor = UserContext.requireUser();
    ForensicCaseEntity entity = requireVersion(id, request.expectedVersion());
    String from = entity.getStatus().name();
    entity.start(actor.userId(), actor.hasPermission(SecurityHeaders.PERM_FORENSICS_ADMIN),
        clock.instant());
    persistTransition(entity, actor.userId(), "START", from, null, null,
        "FORENSIC_CASE_STARTED");
    return mapper.toResponse(entity);
  }

  @Transactional
  public ForensicCaseResponse submit(UUID id, SubmitForensicCaseRequest request) {
    featureGate.requireEnabled();
    GatewayUser actor = UserContext.requireUser();
    ForensicCaseEntity entity = requireVersion(id, request.expectedVersion());
    if (entity.getTransactionId() != null) {
      findingService.synchronize(entity.getTransactionId(), id);
    }
    if (findingService.listForCase(id, entity.getTransactionId()).isEmpty()) {
      throw new BusinessException(
          "FORENSIC_FINDING_REQUIRED", "At least one finding is required before submission");
    }
    String from = entity.getStatus().name();
    EvidenceCompleteness completeness = completeness(entity);
    entity.submit(actor.userId(), actor.hasPermission(SecurityHeaders.PERM_FORENSICS_ADMIN),
        completeness, clock.instant());
    entity.initiateRemediation(clock.instant());
    persistTransition(entity, actor.userId(), "SUBMIT", from, "PENDING_CHECKER",
        request.recommendation(), "FORENSIC_CASE_PENDING_REVIEW");
    return mapper.toResponse(entity);
  }

  @Transactional
  public ForensicCaseResponse approve(UUID id, ApproveForensicResolutionRequest request) {
    featureGate.requireEnabled();
    GatewayUser actor = UserContext.requireUser();
    ForensicCaseEntity entity = requireVersion(id, request.expectedVersion());
    String from = entity.getStatus().name();
    ForensicResolutionCode resolution = parseResolution(request.resolutionCode());
    entity.approve(actor.userId(), resolution, request.resolutionNote().trim(),
        request.systemic(), clock.instant());
    persistTransition(entity, actor.userId(), "APPROVE_RESOLUTION", from, resolution.name(),
        request.resolutionNote(), "FORENSIC_CASE_RESOLVED");
    if (request.systemic() && resolution == ForensicResolutionCode.CONFIRMED_ISSUE) {
      scenarioService.generateFromCase(entity, actor.userId());
    }
    return mapper.toResponse(entity);
  }

  @Transactional
  public ForensicCaseResponse reject(UUID id, RejectForensicResolutionRequest request) {
    featureGate.requireEnabled();
    GatewayUser actor = UserContext.requireUser();
    ForensicCaseEntity entity = requireVersion(id, request.expectedVersion());
    String from = entity.getStatus().name();
    entity.reject(actor.userId(), request.reason().trim(), clock.instant());
    persistTransition(entity, actor.userId(), "REJECT_RESOLUTION", from, "REWORK",
        request.reason(), "FORENSIC_CASE_REWORK_REQUIRED");
    return mapper.toResponse(entity);
  }

  @Transactional
  public ForensicCaseResponse reopen(UUID id, ReopenForensicCaseRequest request) {
    featureGate.requireEnabled();
    GatewayUser actor = UserContext.requireUser();
    ForensicCaseEntity entity = requireVersion(id, request.expectedVersion());
    String from = entity.getStatus().name();
    entity.reopen(clock.instant());
    entity.updateNarrative(null, clock.instant());
    persistTransition(entity, actor.userId(), "REOPEN", from, null, request.reason(),
        "FORENSIC_CASE_REOPENED");
    return mapper.toResponse(entity);
  }

  @Transactional
  public ForensicCaseResponse confirmRootCause(UUID id, ConfirmRootCauseRequest request) {
    featureGate.requireEnabled();
    GatewayUser actor = UserContext.requireUser();
    ForensicCaseEntity entity = requireVersion(id, request.expectedVersion());
    if (entity.getStatus() != ForensicCaseStatus.INVESTIGATING
        && entity.getStatus() != ForensicCaseStatus.ASSIGNED
        && entity.getStatus() != ForensicCaseStatus.OPEN) {
      throw new BusinessException("FORENSIC_CASE_INVALID_STATUS", "Case must be in investigation to confirm root cause");
    }
    Instant now = clock.instant();
    entity.confirmRootCause(now);
    caseRepository.saveAndFlush(entity);
    recordHistory(entity, actor.userId(), "CONFIRM_ROOT_CAUSE", entity.getStatus().name(),
        "ROOT_CAUSE_CONFIRMED", request.note());
    recordAudit(entity, actor.userId(), "FORENSIC_CASE_ROOT_CAUSE_CONFIRMED", "ROOT_CAUSE_CONFIRMED");
    return mapper.toResponse(entity);
  }

  @Transactional
  public ForensicCaseResponse verifyReplay(UUID id, VerifyReplayRequest request) {
    featureGate.requireEnabled();
    GatewayUser actor = UserContext.requireUser();
    ForensicCaseEntity entity = requireVersion(id, request.expectedVersion());
    if (entity.getStatus() != ForensicCaseStatus.INVESTIGATING
        && entity.getStatus() != ForensicCaseStatus.ASSIGNED
        && entity.getStatus() != ForensicCaseStatus.OPEN
        && entity.getStatus() != ForensicCaseStatus.PENDING_CHECKER) {
      throw new BusinessException("FORENSIC_CASE_INVALID_STATUS", "Case must be in active investigation to verify replay");
    }
    Instant now = clock.instant();
    entity.verifyReplay(now);
    caseRepository.saveAndFlush(entity);
    recordHistory(entity, actor.userId(), "VERIFY_REPLAY", entity.getStatus().name(),
        "REPLAY_VERIFIED", request.note() != null ? request.note() : (request.replayRunId() != null ? "Replay run: " + request.replayRunId() : null));
    recordAudit(entity, actor.userId(), "FORENSIC_CASE_REPLAY_VERIFIED", "REPLAY_VERIFIED");
    return mapper.toResponse(entity);
  }

  @Transactional
  public ForensicCaseResponse recordRemediation(UUID id, RecordRemediationRequest request) {
    featureGate.requireEnabled();
    GatewayUser actor = UserContext.requireUser();
    ForensicCaseEntity entity = requireVersion(id, request.expectedVersion());
    List<Map<String, Object>> actions = parseActions(entity.getRemediationJson());
    Instant now = clock.instant();
    Map<String, Object> action = new LinkedHashMap<>();
    action.put("actionType", request.actionType().trim());
    action.put("referenceId", request.referenceId() == null ? null : request.referenceId().trim());
    action.put("description", request.description().trim());
    action.put("completed", request.completed());
    action.put("completedAt", request.completed() ? now.toString() : null);
    actions.add(action);
    String json = jsonSupport.serialize(actions);
    boolean allCompleted = actions.stream().allMatch(a -> Boolean.TRUE.equals(a.get("completed")));
    if (allCompleted) {
      entity.completeRemediation(json, now);
    } else {
      entity.recordRemediation(json, now);
    }
    caseRepository.saveAndFlush(entity);
    recordHistory(entity, actor.userId(), "RECORD_REMEDIATION", entity.getStatus().name(),
        request.actionType(), request.description());
    recordAudit(entity, actor.userId(), "FORENSIC_CASE_REMEDIATION", request.actionType());
    return mapper.toResponse(entity);
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> parseActions(String json) {
    if (json == null || json.isBlank()) return new ArrayList<>();
    Object parsed = jsonSupport.deserializeAny(json);
    if (parsed instanceof List<?> list) {
      return new ArrayList<>(list.stream()
          .filter(item -> item instanceof Map)
          .map(item -> (Map<String, Object>) item)
          .toList());
    }
    return new ArrayList<>();
  }

  @Transactional
  public ForensicFindingResponse addFinding(
      UUID id, CreateForensicFindingRequest request) {
    featureGate.requireEnabled();
    GatewayUser actor = UserContext.requireUser();
    ForensicCaseEntity entity = require(id);
    if (entity.getStatus() != ForensicCaseStatus.INVESTIGATING) {
      throw new BusinessException(
          "FORENSIC_CASE_INVALID_TRANSITION", "Findings can only be added while investigating");
    }
    ForensicFindingResponse response =
        findingService.createManual(id, entity.getTransactionId(), request);
    recordAudit(entity, actor.userId(), "FORENSIC_FINDING_CREATE", response.id());
    return response;
  }

  private void persistTransition(
      ForensicCaseEntity entity, UUID actor, String action, String from, String decision,
      String note, String eventType) {
    caseRepository.saveAndFlush(entity);
    recordHistory(entity, actor, action, from, decision, note);
    recordAudit(entity, actor, "FORENSIC_CASE_" + action, decision);
    eventPublisher.publish(eventType, entity);
  }

  private void recordHistory(
      ForensicCaseEntity entity, UUID actor, String action, String from,
      String decision, String note) {
    historyRepository.save(ForensicCaseHistoryEntity.of(
        entity.getId(), actor, action, from, entity.getStatus().name(), decision,
        trim(note), entity.getVersion(), clock.instant()));
  }

  private void recordAudit(
      ForensicCaseEntity entity, UUID actor, String action, String decision) {
    String metadata = jsonSupport.serialize(Map.of(
        "caseNumber", entity.getCaseNumber(),
        "status", entity.getStatus().name(),
        "version", entity.getVersion(),
        "decision", decision == null ? "" : decision));
    auditCommandService.recordInternalAudit(new CreateAuditLogCommand(
        actor, action, "FORENSIC_CASE", entity.getId().toString(), UserContext.clientIp(), metadata));
  }

  private EvidenceCompleteness completeness(ForensicCaseEntity entity) {
    if (entity.getTransactionId() == null) {
      return EvidenceCompleteness.PARTIAL;
    }
    UUID transactionId = entity.getTransactionId();
    boolean saga = !sagaRepository.findByTransferIdOrderByCreatedAtAsc(transactionId).isEmpty();
    boolean outbox = !outboxRepository.findByAggregateIdOrderByCreatedAtAsc(transactionId).isEmpty();
    boolean audit = !auditRepository.findByResourceIdOrderByCreatedAtAsc(transactionId.toString()).isEmpty();
    return saga && outbox && audit ? EvidenceCompleteness.COMPLETE : EvidenceCompleteness.PARTIAL;
  }

  private ForensicCaseEntity requireVersion(UUID id, long expectedVersion) {
    ForensicCaseEntity entity = require(id);
    if (entity.getVersion() != expectedVersion) {
      throw new BusinessException(
          "FORENSIC_CASE_CONCURRENT_MODIFICATION", "Forensic case was modified by another user",
          HttpStatus.CONFLICT);
    }
    return entity;
  }

  private ForensicCaseEntity require(UUID id) {
    return caseRepository.findById(id).orElseThrow(() ->
        new BusinessException("FORENSIC_CASE_NOT_FOUND", "Forensic case not found"));
  }

  private void validateSubject(UUID transactionId, UUID accountId) {
    if (transactionId == null && accountId == null) {
      throw new BusinessException(
          "FORENSIC_CASE_SUBJECT_REQUIRED", "Transaction or account subject is required");
    }
    if (transactionId != null && !transferRepository.existsById(transactionId)) {
      throw new BusinessException(
          "FORENSIC_TRANSACTION_NOT_FOUND", "Transaction investigation not found");
    }
  }

  private String normalizeSourceType(String value) {
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    if (!SOURCE_TYPES.contains(normalized)) {
      throw new BusinessException("INVALID_FORENSIC_SOURCE_TYPE", "Unsupported forensic source type");
    }
    return normalized;
  }

  private ForensicCasePriority parsePriority(String value) {
    try {
      return ForensicCasePriority.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new BusinessException("INVALID_FORENSIC_CASE_PRIORITY", "Unsupported case priority");
    }
  }

  private ForensicResolutionCode parseResolution(String value) {
    try {
      return ForensicResolutionCode.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new BusinessException("INVALID_FORENSIC_RESOLUTION", "Unsupported resolution code");
    }
  }

  private String caseNumber(UUID id, Instant now) {
    String day = LocalDate.ofInstant(now, ZoneOffset.UTC)
        .format(DateTimeFormatter.BASIC_ISO_DATE);
    return "FC-" + day + "-" + id.toString().substring(0, 8).toUpperCase(Locale.ROOT);
  }

  private String trim(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
