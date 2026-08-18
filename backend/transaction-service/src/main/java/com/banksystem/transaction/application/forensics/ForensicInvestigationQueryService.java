package com.banksystem.transaction.application.forensics;

import com.banksystem.common.api.PageResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.api.dto.ForensicDtos.InvestigationDetailResponse;
import com.banksystem.transaction.api.dto.ForensicDtos.InvestigationItemResponse;
import com.banksystem.transaction.api.dto.ForensicDtos.CausalGraphResponse;
import com.banksystem.transaction.api.dto.ForensicDtos.TimelineEvidenceResponse;
import com.banksystem.transaction.api.dto.ForensicDtos.TemporalAccountStateResponse;
import com.banksystem.transaction.api.dto.ForensicDtos.TemporalInvestigationStateResponse;
import com.banksystem.transaction.application.forensics.LedgerEvidenceGateway.AccountStateEvidence;
import java.time.Instant;
import java.util.ArrayList;
import com.banksystem.transaction.domain.audit.AuditLogEntity;
import com.banksystem.transaction.domain.audit.AuditLogRepository;
import com.banksystem.transaction.domain.outbox.OutboxEventEntity;
import com.banksystem.transaction.domain.outbox.OutboxEventRepository;
import com.banksystem.transaction.domain.reconciliation.ReconItemEntity;
import com.banksystem.transaction.domain.reconciliation.ReconItemRepository;
import com.banksystem.transaction.domain.transfer.SagaStepLogEntity;
import com.banksystem.transaction.domain.transfer.SagaStepLogRepository;
import com.banksystem.transaction.domain.transfer.TransferOrderEntity;
import com.banksystem.transaction.domain.transfer.TransferOrderRepository;
import com.banksystem.transaction.domain.transfer.TransferStatus;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only use case that loads the evidence required for a forensic investigation. */
@Service
public class ForensicInvestigationQueryService {

  private static final Logger log = LoggerFactory.getLogger(ForensicInvestigationQueryService.class);
  private static final UUID EMPTY_UUID = new UUID(0L, 0L);

  private final TransferOrderRepository transferRepository;
  private final SagaStepLogRepository sagaRepository;
  private final OutboxEventRepository outboxRepository;
  private final ReconItemRepository reconciliationRepository;
  private final AuditLogRepository auditRepository;
  private final ForensicEvidenceAssembler assembler;
  private final ForensicsFeatureGate featureGate;
  private final LedgerEvidenceGateway ledgerEvidenceGateway;
  private final ForensicGraphCacheService graphCacheService;

  public ForensicInvestigationQueryService(
      TransferOrderRepository transferRepository,
      SagaStepLogRepository sagaRepository,
      OutboxEventRepository outboxRepository,
      ReconItemRepository reconciliationRepository,
      AuditLogRepository auditRepository,
      ForensicEvidenceAssembler assembler,
      ForensicsFeatureGate featureGate,
      LedgerEvidenceGateway ledgerEvidenceGateway,
      ForensicGraphCacheService graphCacheService) {
    this.transferRepository = transferRepository;
    this.sagaRepository = sagaRepository;
    this.outboxRepository = outboxRepository;
    this.reconciliationRepository = reconciliationRepository;
    this.auditRepository = auditRepository;
    this.assembler = assembler;
    this.featureGate = featureGate;
    this.ledgerEvidenceGateway = ledgerEvidenceGateway;
    this.graphCacheService = graphCacheService;
  }

  @Transactional(readOnly = true)
  public PageResponse<InvestigationItemResponse> search(ForensicSearchQuery query) {
    featureGate.requireEnabled();
    Page<TransferOrderEntity> result = transferRepository.searchForensics(
        query.status() != null,
        query.status() == null ? TransferStatus.PENDING : query.status(),
        query.transactionId() != null,
        query.transactionId() == null ? EMPTY_UUID : query.transactionId(),
        query.accountId() != null,
        query.accountId() == null ? EMPTY_UUID : query.accountId(),
        query.riskDecision() != null,
        query.riskDecision() == null ? "" : query.riskDecision(),
        query.q() != null,
        query.q() == null ? "" : query.q(),
        query.from(),
        query.to(),
        PageRequest.of(query.page(), query.size()));
    log.info("[FORENSICS-QUERY] Search query=[{}] status=[{}] Page={} Size={} Results={}",
        query.q(), query.status(), query.page(), query.size(), result.getTotalElements());
    return new PageResponse<>(
        result.getContent().stream().map(assembler::toItem).toList(),
        result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
  }

  @Transactional(readOnly = true)
  public InvestigationDetailResponse get(UUID transactionId) {
    featureGate.requireEnabled();
    TransferOrderEntity transfer = transferRepository.findById(transactionId)
        .orElseThrow(() -> new BusinessException(
            "FORENSIC_TRANSACTION_NOT_FOUND", "Transaction investigation not found"));
    List<SagaStepLogEntity> saga = sagaRepository.findByTransferIdOrderByCreatedAtAsc(transactionId);
    List<OutboxEventEntity> outbox =
        outboxRepository.findByAggregateIdOrderByCreatedAtAsc(transactionId);
    List<ReconItemEntity> reconciliation =
        reconciliationRepository.findByTransferIdOrderByKindAsc(transactionId);
    List<AuditLogEntity> audit =
        auditRepository.findByResourceIdOrderByCreatedAtAsc(transactionId.toString());
    InvestigationDetailResponse detail = assembler.toDetail(
        transfer, saga, outbox, reconciliation, audit,
        ledgerEvidenceGateway.findByTransactionId(transactionId));
    log.info("[FORENSICS-DETAIL] Loaded detail for Tx=[{}] Status=[{}] Saga=[{}] Outbox=[{}] Recon=[{}] Audit=[{}] LedgerCompleteness=[{}]",
        transactionId, transfer.getStatus(), saga.size(), outbox.size(), reconciliation.size(), audit.size(), detail.ledgerEvidence().completeness());
    return detail;
  }

  @Transactional(readOnly = true)
  public CausalGraphResponse causalGraph(UUID transactionId) {
    InvestigationDetailResponse detail = get(transactionId);
    Instant watermark = detail.transaction().updatedAt();
    CausalGraphResponse cached = graphCacheService.find(transactionId, watermark);
    if (cached != null) {
      log.info("[CAUSAL-GRAPH] Cache hit for Tx=[{}] Nodes=[{}] Edges=[{}]",
          transactionId, cached.nodes().size(), cached.edges().size());
      return cached;
    }
    CausalGraphResponse stored = graphCacheService.store(transactionId, watermark, detail.causalGraph());
    log.info("[CAUSAL-GRAPH] Generated & cached graph for Tx=[{}] Nodes=[{}] Edges=[{}] FirstAnomaly=[{}]",
        transactionId, stored.nodes().size(), stored.edges().size(), stored.firstAnomalousNodeId());
    return stored;
  }

  @Transactional(readOnly = true)
  public PageResponse<TimelineEvidenceResponse> timeline(
      UUID transactionId, EvidenceTimelineQuery query) {
    List<TimelineEvidenceResponse> all = get(transactionId).timeline().stream()
        .filter(item -> query.source() == null || query.source().equalsIgnoreCase(item.source()))
        .toList();
    int from = Math.min(query.page() * query.size(), all.size());
    int to = Math.min(from + query.size(), all.size());
    int totalPages = all.isEmpty() ? 0 : (int) Math.ceil((double) all.size() / query.size());
    return new PageResponse<>(all.subList(from, to), query.page(), query.size(), all.size(), totalPages);
  }

  @Transactional(readOnly = true)
  public TemporalInvestigationStateResponse temporalState(UUID transactionId, Instant at) {
    featureGate.requireEnabled();
    TransferOrderEntity transfer = transferRepository.findById(transactionId)
        .orElseThrow(() -> new BusinessException(
            "FORENSIC_TRANSACTION_NOT_FOUND", "Transaction investigation not found"));
    List<SagaStepLogEntity> saga = sagaRepository.findByTransferIdOrderByCreatedAtAsc(transactionId);
    List<TemporalAccountStateResponse> states = new ArrayList<>();
    List<String> missing = new ArrayList<>();
    appendAccountState(transfer.getFromAccountId(), at, states, missing);
    if (transfer.getToAccountId() != null) {
      appendAccountState(transfer.getToAccountId(), at, states, missing);
    }
    String transactionState = transactionStateAt(transfer, saga, at);
    return new TemporalInvestigationStateResponse(
        transactionId.toString(), at, transactionState, List.copyOf(states), List.copyOf(missing),
        missing.isEmpty() ? "COMPLETE" : "PARTIAL");
  }

  @Transactional(readOnly = true)
  public TemporalAccountStateResponse temporalAccountState(UUID accountId, Instant at) {
    featureGate.requireEnabled();
    AccountStateEvidence evidence = ledgerEvidenceGateway.findAccountState(accountId, at)
        .orElseThrow(() -> new BusinessException(
            "FORENSIC_TEMPORAL_STATE_UNAVAILABLE", "Temporal account state is unavailable"));
    return new TemporalAccountStateResponse(
        evidence.accountId(), evidence.currency(), evidence.ledgerBalance(),
        evidence.activeHoldAmount(), evidence.availableBalance(), evidence.completeness());
  }

  private void appendAccountState(
      UUID accountId,
      Instant at,
      List<TemporalAccountStateResponse> states,
      List<String> missing) {
    AccountStateEvidence evidence = ledgerEvidenceGateway.findAccountState(accountId, at).orElse(null);
    if (evidence == null) {
      missing.add("ACCOUNT_STATE:" + accountId);
      return;
    }
    states.add(new TemporalAccountStateResponse(
        evidence.accountId(), evidence.currency(), evidence.ledgerBalance(),
        evidence.activeHoldAmount(), evidence.availableBalance(), evidence.completeness()));
  }

  private String transactionStateAt(
      TransferOrderEntity transfer, List<SagaStepLogEntity> saga, Instant at) {
    if (at.isBefore(transfer.getCreatedAt())) return "NOT_CREATED";
    if (!at.isBefore(transfer.getUpdatedAt())) return transfer.getStatus().name();
    return saga.stream()
        .filter(step -> !step.getCreatedAt().isAfter(at))
        .reduce((first, second) -> second)
        .map(step -> "SAGA:" + step.getStep() + ":" + step.getStatus())
        .orElse("INITIATED");
  }
}
