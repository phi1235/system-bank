package com.banksystem.transaction.application.forensics;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.domain.forensics.ForensicCaseEntity;
import com.banksystem.transaction.domain.forensics.ForensicCaseHistoryEntity;
import com.banksystem.transaction.domain.forensics.ForensicCaseHistoryRepository;
import com.banksystem.transaction.domain.forensics.ForensicCasePriority;
import com.banksystem.transaction.domain.forensics.ForensicCaseRepository;
import com.banksystem.transaction.domain.forensics.ForensicCaseStatus;
import com.banksystem.transaction.domain.forensics.ForensicFindingEntity;
import com.banksystem.transaction.domain.forensics.ForensicFindingRepository;
import com.banksystem.transaction.domain.forensics.InvestigationStage;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ForensicCaseOrchestratorService {
  private static final Logger log = LoggerFactory.getLogger(ForensicCaseOrchestratorService.class);
  private static final List<ForensicCaseStatus> ACTIVE_STATUSES = List.of(
      ForensicCaseStatus.OPEN,
      ForensicCaseStatus.ASSIGNED,
      ForensicCaseStatus.INVESTIGATING,
      ForensicCaseStatus.PENDING_CHECKER,
      ForensicCaseStatus.REOPENED
  );

  private final ForensicCaseRepository caseRepository;
  private final ForensicCaseHistoryRepository historyRepository;
  private final ForensicFindingRepository findingRepository;
  private final ForensicFindingService findingService;
  private final ForensicInvestigationQueryService investigationQueryService;
  private final Clock clock;
  private final boolean autoCaseEnabled;
  private final Semaphore causalSemaphore;

  public ForensicCaseOrchestratorService(
      ForensicCaseRepository caseRepository,
      ForensicCaseHistoryRepository historyRepository,
      ForensicFindingRepository findingRepository,
      ForensicFindingService findingService,
      ForensicInvestigationQueryService investigationQueryService,
      Clock clock,
      @Value("${bank.forensics.orchestrator.auto-case-enabled:true}") boolean autoCaseEnabled,
      @Value("${bank.forensics.orchestrator.causal-concurrency:3}") int causalConcurrency) {
    this.caseRepository = caseRepository;
    this.historyRepository = historyRepository;
    this.findingRepository = findingRepository;
    this.findingService = findingService;
    this.investigationQueryService = investigationQueryService;
    this.clock = clock;
    this.autoCaseEnabled = autoCaseEnabled;
    this.causalSemaphore = new Semaphore(Math.max(1, causalConcurrency));
  }

  @Transactional
  public ForensicCaseEntity orchestrateForTransaction(UUID transactionId, UUID actor) {
    if (!autoCaseEnabled || transactionId == null) {
      return null;
    }

    log.info("[FORENSIC-ORCHESTRATOR] Orchestrating case for Tx=[{}] Actor=[{}]", transactionId, actor);

    // 1. Synchronize findings from transaction state
    findingService.synchronize(transactionId, null);
    List<ForensicFindingEntity> findings = findingRepository.findByTransactionIdOrderByDetectedAtDesc(transactionId);
    if (findings.isEmpty()) {
      log.debug("[FORENSIC-ORCHESTRATOR] No findings detected for Tx=[{}]", transactionId);
      return null;
    }

    // 2. Check if active case already exists for this transaction
    List<ForensicCaseEntity> activeCases = caseRepository.findByTransactionIdAndStatusInOrderByCreatedAtDesc(
        transactionId, ACTIVE_STATUSES);

    ForensicCaseEntity targetCase;
    Instant now = clock.instant();

    if (!activeCases.isEmpty()) {
      targetCase = activeCases.get(0);
      log.info("[FORENSIC-ORCHESTRATOR] Attached to existing active case [{}] (#{}) for Tx=[{}]",
          targetCase.getId(), targetCase.getCaseNumber(), transactionId);
    } else {
      // 3. Create new case with DB unique index protection
      ForensicFindingEntity primaryFinding = findings.get(0);
      ForensicCasePriority priority = mapPriority(primaryFinding.getSeverity());
      String caseNumber = generateCaseNumber(now);
      String title = "[Tự động] Vi phạm " + primaryFinding.getRuleCode() + " trên giao dịch " + transactionId;
      String summary = primaryFinding.getDetail() != null ? primaryFinding.getDetail() : primaryFinding.getTitle();
      UUID caseActor = actor != null ? actor : UUID.randomUUID();

      ForensicCaseEntity newCase = ForensicCaseEntity.create(
          UUID.randomUUID(),
          caseNumber,
          transactionId,
          null,
          "AUTO_VERIFICATION",
          primaryFinding.getFindingKey(),
          priority,
          title,
          summary,
          caseActor,
          now
      );
      newCase.markViolationDetected(now);

      try {
        targetCase = caseRepository.saveAndFlush(newCase);
        historyRepository.save(ForensicCaseHistoryEntity.of(
            targetCase.getId(), targetCase.getCreatedBy(), "AUTO_CREATED",
            null, targetCase.getStatus().name(), "CASE_CREATED",
            "Tự động tạo case từ vi phạm bất biến: " + primaryFinding.getRuleCode(),
            targetCase.getVersion(), now));
        log.info("[FORENSIC-ORCHESTRATOR] Created new case [{}] (#{}) Priority=[{}] Rule=[{}] for Tx=[{}]",
            targetCase.getId(), targetCase.getCaseNumber(), targetCase.getPriority(), primaryFinding.getRuleCode(), transactionId);
      } catch (DataIntegrityViolationException ex) {
        log.warn("[FORENSIC-ORCHESTRATOR] Unique active case constraint triggered for Tx=[{}]. Falling back to existing active case.", transactionId);
        List<ForensicCaseEntity> fallbackList = caseRepository.findByTransactionIdAndStatusInOrderByCreatedAtDesc(
            transactionId, ACTIVE_STATUSES);
        if (!fallbackList.isEmpty()) {
          targetCase = fallbackList.get(0);
        } else {
          throw new BusinessException("FORENSIC_CASE_CREATION_FAILED", "Failed to resolve active forensic case");
        }
      }
    }

    // 4. Attach unlinked findings to the active case
    for (ForensicFindingEntity finding : findings) {
      if (finding.getCaseId() == null || !finding.getCaseId().equals(targetCase.getId())) {
        finding.attachToCase(targetCase.getId());
        findingRepository.save(finding);
      }
    }

    // 5. Automatically extract & attach Causal Graph, advancing stage to CAUSAL_GRAPH_ATTACHED (concurrency guarded)
    if (targetCase.getInvestigationStage() == InvestigationStage.INITIALIZED
        || targetCase.getInvestigationStage() == InvestigationStage.VIOLATION_DETECTED) {
      boolean acquired = causalSemaphore.tryAcquire();
      if (acquired) {
        try {
          investigationQueryService.get(transactionId);
          targetCase.attachCausalGraph(now);
          caseRepository.save(targetCase);
          historyRepository.save(ForensicCaseHistoryEntity.of(
              targetCase.getId(), targetCase.getCreatedBy(), "CAUSAL_GRAPH_ATTACHED",
              targetCase.getStatus().name(), targetCase.getStatus().name(), "GRAPH_LINKED",
              "Tự động trích xuất và gắn Causal Graph vào case",
              targetCase.getVersion(), now));
          log.info("[STAGE-TRANSITION] Case [{}] (#{}) Stage advanced to [CAUSAL_GRAPH_ATTACHED] for Tx=[{}]",
              targetCase.getId(), targetCase.getCaseNumber(), transactionId);
        } catch (Exception e) {
          log.warn("[FORENSIC-ORCHESTRATOR] Failed to auto-extract causal graph for Tx=[{}]: {}", transactionId, e.getMessage());
        } finally {
          causalSemaphore.release();
        }
      } else {
        log.info("[FORENSIC-ORCHESTRATOR] Causal graph worker busy, skipping synchronous extraction for Tx=[{}]", transactionId);
      }
    }

    return targetCase;
  }

  private ForensicCasePriority mapPriority(String severity) {
    if (severity == null) {
      return ForensicCasePriority.MEDIUM;
    }
    return switch (severity.trim().toUpperCase()) {
      case "CRITICAL" -> ForensicCasePriority.CRITICAL;
      case "HIGH" -> ForensicCasePriority.HIGH;
      case "LOW", "INFO" -> ForensicCasePriority.LOW;
      default -> ForensicCasePriority.MEDIUM;
    };
  }

  private String generateCaseNumber(Instant now) {
    String datePart = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC).format(now);
    return "FC-" + datePart + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
  }
}
