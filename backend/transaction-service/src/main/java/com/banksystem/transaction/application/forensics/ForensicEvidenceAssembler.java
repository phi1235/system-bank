package com.banksystem.transaction.application.forensics;

import com.banksystem.transaction.api.dto.ForensicDtos.AuditEvidenceResponse;
import com.banksystem.transaction.api.dto.ForensicDtos.InvestigationDetailResponse;
import com.banksystem.transaction.api.dto.ForensicDtos.InvestigationItemResponse;
import com.banksystem.transaction.api.dto.ForensicDtos.FinancialEventEvidenceResponse;
import com.banksystem.transaction.api.dto.ForensicDtos.LedgerEvidenceResponse;
import com.banksystem.transaction.api.dto.ForensicDtos.LedgerHoldEvidenceResponse;
import com.banksystem.transaction.api.dto.ForensicDtos.LedgerJournalEvidenceResponse;
import com.banksystem.transaction.api.dto.ForensicDtos.LedgerPostingEvidenceResponse;
import com.banksystem.transaction.api.dto.ForensicDtos.CausalEdgeResponse;
import com.banksystem.transaction.api.dto.ForensicDtos.CausalGraphResponse;
import com.banksystem.transaction.api.dto.ForensicDtos.CausalNodeResponse;
import com.banksystem.transaction.api.dto.ForensicDtos.FinancialViolationResponse;
import com.banksystem.transaction.api.dto.ForensicDtos.OutboxEvidenceResponse;
import com.banksystem.transaction.api.dto.ForensicDtos.ReconciliationEvidenceResponse;
import com.banksystem.transaction.api.dto.ForensicDtos.SagaEvidenceResponse;
import com.banksystem.transaction.api.dto.ForensicDtos.TimelineEvidenceResponse;
import com.banksystem.transaction.domain.audit.AuditLogEntity;
import com.banksystem.transaction.domain.outbox.OutboxEventEntity;
import com.banksystem.transaction.domain.reconciliation.ReconItemEntity;
import com.banksystem.transaction.domain.transfer.SagaStepLogEntity;
import com.banksystem.transaction.domain.transfer.TransferOrderEntity;
import com.banksystem.transaction.domain.transfer.TransferStatus;
import com.banksystem.transaction.application.forensics.LedgerEvidenceGateway.FinancialEventEvidence;
import com.banksystem.transaction.application.forensics.LedgerEvidenceGateway.HoldEvidence;
import com.banksystem.transaction.application.forensics.LedgerEvidenceGateway.JournalEvidence;
import com.banksystem.transaction.application.forensics.LedgerEvidenceGateway.PostingEvidence;
import com.banksystem.transaction.application.forensics.LedgerEvidenceGateway.TransactionLedgerEvidence;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Pure assembler and deterministic signal classifier for the forensic read model. */
@Component
class ForensicEvidenceAssembler {
  private final ForensicFailureSignatureService failureSignatureService;

  ForensicEvidenceAssembler(ForensicFailureSignatureService failureSignatureService) {
    this.failureSignatureService = failureSignatureService;
  }

  InvestigationDetailResponse toDetail(
      TransferOrderEntity transfer,
      List<SagaStepLogEntity> sagaEntities,
      List<OutboxEventEntity> outboxEntities,
      List<ReconItemEntity> reconciliationEntities,
      List<AuditLogEntity> auditEntities,
      Optional<TransactionLedgerEvidence> ledgerSnapshot) {
    List<SagaEvidenceResponse> saga = sagaEntities.stream().map(this::toSaga).toList();
    List<OutboxEvidenceResponse> outbox = outboxEntities.stream().map(this::toOutbox).toList();
    List<ReconciliationEvidenceResponse> reconciliation =
        reconciliationEntities.stream().map(this::toReconciliation).toList();
    List<AuditEvidenceResponse> audit = auditEntities.stream().map(this::toAudit).toList();
    LedgerEvidenceResponse ledger = toLedger(ledgerSnapshot);
    List<String> missingSources = missingSources(saga, outbox, audit, ledger);
    List<FinancialViolationResponse> violations = verifyLedger(ledger);
    List<TimelineEvidenceResponse> timeline = timeline(transfer, saga, outbox, audit, ledger);
    String signal = primaryEvidenceSignal(transfer, sagaEntities, outboxEntities,
        reconciliationEntities);
    return new InvestigationDetailResponse(
        toItem(transfer, signal),
        missingSources.isEmpty() ? "COMPLETE" : "PARTIAL",
        missingSources,
        saga,
        outbox,
        reconciliation,
        audit,
        ledger,
        violations,
        causalGraph(transfer, saga, outbox, reconciliation, audit, ledger, violations),
        timeline);
  }

  InvestigationItemResponse toItem(TransferOrderEntity transfer) {
    return toItem(transfer, primarySignal(transfer));
  }

  private InvestigationItemResponse toItem(TransferOrderEntity transfer, String signal) {
    return new InvestigationItemResponse(
        transfer.getId().toString(), transfer.getStatus().name(), transfer.getTransferType(),
        transfer.getFromAccountId().toString(),
        transfer.getToAccountId() == null ? null : transfer.getToAccountId().toString(),
        transfer.getToAccountNumber(), transfer.getTargetBankCode(), transfer.getTargetAccountName(),
        transfer.getAmount(), transfer.getFeeAmount(), transfer.getCurrency(), transfer.getRiskDecision(),
        transfer.getRiskScore(), transfer.getProviderStatus(), transfer.getFailureReason(),
        !"NONE".equals(signal), signal, transfer.getCreatedAt(), transfer.getUpdatedAt());
  }

  private String primaryEvidenceSignal(
      TransferOrderEntity transfer,
      List<SagaStepLogEntity> saga,
      List<OutboxEventEntity> outbox,
      List<ReconItemEntity> reconciliation) {
    boolean mismatch = reconciliation.stream().anyMatch(item ->
        item.getExpectedAmount() != null
            && item.getActualAmount() != null
            && item.getExpectedAmount().compareTo(item.getActualAmount()) != 0);
    if (mismatch) {
      return "RECONCILIATION_MISMATCH";
    }
    if (outbox.stream().anyMatch(item -> "DEAD".equalsIgnoreCase(item.getStatus()))) {
      return "OUTBOX_DEAD";
    }
    if (saga.stream().anyMatch(item -> isOneOf(item.getStatus(), "FAILED", "ERROR"))) {
      return "SAGA_FAILED";
    }
    return primarySignal(transfer);
  }

  private String primarySignal(TransferOrderEntity transfer) {
    if (transfer.getStatus() == TransferStatus.UNKNOWN) {
      return "PROVIDER_STATUS_UNKNOWN";
    }
    if (transfer.getStatus() == TransferStatus.REVIEW_REQUIRED) {
      return "MANUAL_REVIEW_REQUIRED";
    }
    if (transfer.getStatus() == TransferStatus.COMPENSATING
        || transfer.getStatus() == TransferStatus.COMPENSATED) {
      return "COMPENSATION";
    }
    if (transfer.getStatus() == TransferStatus.FAILED) {
      return "TRANSFER_FAILED";
    }
    if (transfer.getStatus() == TransferStatus.RISK_REVIEW
        || isOneOf(transfer.getRiskDecision(), "REVIEW", "BLOCK")) {
      return "RISK_REVIEW";
    }
    return "NONE";
  }

  private SagaEvidenceResponse toSaga(SagaStepLogEntity entity) {
    return new SagaEvidenceResponse(
        entity.getId().toString(), entity.getStep(), entity.getStatus(), entity.getDetail(),
        entity.getCreatedAt());
  }

  private OutboxEvidenceResponse toOutbox(OutboxEventEntity entity) {
    return new OutboxEvidenceResponse(
        entity.getId().toString(), entity.getEventType(), entity.getStatus(),
        entity.getAttemptCount(), entity.getLastError(), entity.getCreatedAt(),
        entity.getPublishedAt());
  }

  private ReconciliationEvidenceResponse toReconciliation(ReconItemEntity entity) {
    return new ReconciliationEvidenceResponse(
        entity.getId().toString(), entity.getRunId().toString(), entity.getKind(),
        entity.getEntryRef(), entity.getExpectedAmount(), entity.getActualAmount(), entity.getDetail());
  }

  private AuditEvidenceResponse toAudit(AuditLogEntity entity) {
    return new AuditEvidenceResponse(
        entity.getId().toString(),
        entity.getActorUserId() == null ? null : entity.getActorUserId().toString(),
        entity.getAction(), entity.getResourceType(), entity.getMetadata(), entity.getCreatedAt());
  }

  private List<String> missingSources(
      List<SagaEvidenceResponse> saga,
      List<OutboxEvidenceResponse> outbox,
      List<AuditEvidenceResponse> audit,
      LedgerEvidenceResponse ledger) {
    List<String> missing = new ArrayList<>();
    if (saga.isEmpty()) {
      missing.add("SAGA");
    }
    if (outbox.isEmpty()) {
      missing.add("OUTBOX");
    }
    if (audit.isEmpty()) {
      missing.add("AUDIT");
    }
    if (!ledger.available() || "EMPTY".equals(ledger.completeness())) {
      missing.add("LEDGER");
    }
    return List.copyOf(missing);
  }

  private List<TimelineEvidenceResponse> timeline(
      TransferOrderEntity transfer,
      List<SagaEvidenceResponse> saga,
      List<OutboxEvidenceResponse> outbox,
      List<AuditEvidenceResponse> audit,
      LedgerEvidenceResponse ledger) {
    List<TimelineEvidenceResponse> events = new ArrayList<>();
    events.add(new TimelineEvidenceResponse(
        "TRANSFER", transfer.getId().toString(), "TRANSFER_CREATED",
        transfer.getStatus().name(), null, transfer.getCreatedAt()));
    saga.forEach(item -> events.add(new TimelineEvidenceResponse(
        "SAGA", item.id(), item.step(), item.status(), item.detail(), item.occurredAt())));
    outbox.forEach(item -> events.add(new TimelineEvidenceResponse(
        "OUTBOX", item.id(), item.eventType(), item.status(), item.lastError(), item.occurredAt())));
    audit.forEach(item -> events.add(new TimelineEvidenceResponse(
        "AUDIT", item.id(), item.action(), null, item.detail(), item.occurredAt())));
    ledger.journals().forEach(item -> events.add(new TimelineEvidenceResponse(
        "LEDGER", item.id(), item.journalType(), item.status(), item.description(),
        item.postedAt() == null ? item.createdAt() : item.postedAt())));
    ledger.events().forEach(item -> events.add(new TimelineEvidenceResponse(
        "FINANCIAL_EVENT", item.eventId(), item.eventType(), null,
        "schemaVersion=" + item.schemaVersion(), item.occurredAt())));
    events.sort(Comparator.comparing(
        TimelineEvidenceResponse::occurredAt,
        Comparator.nullsLast(Comparator.naturalOrder())));
    return List.copyOf(events);
  }

  private LedgerEvidenceResponse toLedger(Optional<TransactionLedgerEvidence> snapshot) {
    if (snapshot.isEmpty()) {
      return new LedgerEvidenceResponse(false, "UNAVAILABLE", List.of(), List.of(), List.of());
    }
    TransactionLedgerEvidence source = snapshot.get();
    return new LedgerEvidenceResponse(
        true,
        source.completeness(),
        source.journals().stream().map(this::toJournal).toList(),
        source.holds().stream().map(this::toHold).toList(),
        source.events().stream().map(this::toFinancialEvent).toList());
  }

  private LedgerJournalEvidenceResponse toJournal(JournalEvidence source) {
    return new LedgerJournalEvidenceResponse(
        source.id(), source.businessCommandId(), source.businessReference(), source.journalType(),
        source.status(), source.currency(), source.description(), source.reversalOfJournalId(),
        source.sequenceNo(), source.createdAt(), source.postedAt(),
        source.postings().stream().map(this::toPosting).toList());
  }

  private LedgerPostingEvidenceResponse toPosting(PostingEvidence source) {
    return new LedgerPostingEvidenceResponse(
        source.id(), source.accountId(), source.ledgerAccountCode(), source.side(), source.amount(),
        source.currency(), source.createdAt());
  }

  private LedgerHoldEvidenceResponse toHold(HoldEvidence source) {
    return new LedgerHoldEvidenceResponse(
        source.id(), source.accountId(), source.amount(), source.currency(), source.status(),
        source.expiresAt(), source.capturedJournalId(), source.createdAt(), source.updatedAt());
  }

  private FinancialEventEvidenceResponse toFinancialEvent(FinancialEventEvidence source) {
    return new FinancialEventEvidenceResponse(
        source.eventId(), source.aggregateType(), source.aggregateId(), source.sequenceNo(),
        source.eventType(), source.schemaVersion(), source.occurredAt(), source.payload(),
        source.payloadSha256());
  }

  private List<FinancialViolationResponse> verifyLedger(LedgerEvidenceResponse ledger) {
    List<FinancialViolationResponse> violations = new ArrayList<>();
    for (LedgerJournalEvidenceResponse journal : ledger.journals()) {
      BigDecimal debits = journal.postings().stream()
          .filter(posting -> "DEBIT".equals(posting.side()))
          .map(LedgerPostingEvidenceResponse::amount)
          .reduce(BigDecimal.ZERO, BigDecimal::add);
      BigDecimal credits = journal.postings().stream()
          .filter(posting -> "CREDIT".equals(posting.side()))
          .map(LedgerPostingEvidenceResponse::amount)
          .reduce(BigDecimal.ZERO, BigDecimal::add);
      if (journal.postings().size() < 2 || debits.compareTo(credits) != 0) {
        violations.add(new FinancialViolationResponse(
            "INV-JOURNAL-001", "CRITICAL", "OPEN", "Journal debit and credit totals differ",
            List.of(journal.id())));
      }
      boolean currencyMismatch = journal.postings().stream()
          .anyMatch(posting -> !journal.currency().equals(posting.currency()));
      if (currencyMismatch) {
        violations.add(new FinancialViolationResponse(
            "INV-CURRENCY-001", "CRITICAL", "OPEN", "Posting currency differs from journal",
            List.of(journal.id())));
      }
      if ("REVERSAL".equals(journal.journalType()) && journal.reversalOfJournalId() == null) {
        violations.add(new FinancialViolationResponse(
            "INV-REVERSAL-001", "CRITICAL", "OPEN", "Reversal does not reference an original journal",
            List.of(journal.id())));
      }
    }
    return List.copyOf(violations);
  }

  private CausalGraphResponse causalGraph(
      TransferOrderEntity transfer,
      List<SagaEvidenceResponse> saga,
      List<OutboxEvidenceResponse> outbox,
      List<ReconciliationEvidenceResponse> reconciliation,
      List<AuditEvidenceResponse> audit,
      LedgerEvidenceResponse ledger,
      List<FinancialViolationResponse> violations) {
    List<CausalNodeResponse> nodes = new ArrayList<>();
    List<CausalEdgeResponse> edges = new ArrayList<>();
    String root = "TRANSFER:" + transfer.getId();
    nodes.add(new CausalNodeResponse(
        root, "TRANSFER", transfer.getId().toString(), transfer.getStatus().name(),
        transfer.getCreatedAt(), transfer.getStatus() == TransferStatus.FAILED));

    saga.forEach(item -> addGraphNode(
        nodes, edges, root, "SAGA:" + item.id(), "SAGA", item.step(), item.status(),
        item.occurredAt(), isOneOf(item.status(), "FAILED", "ERROR"), "HAS_STEP"));
    ledger.journals().forEach(journal -> {
      String journalNode = "JOURNAL:" + journal.id();
      boolean anomalous = violations.stream().anyMatch(item -> item.evidenceIds().contains(journal.id()));
      addGraphNode(nodes, edges, root, journalNode, "JOURNAL", journal.journalType(),
          journal.status(), journal.createdAt(), anomalous, "HAS_FINANCIAL_OUTCOME");
      journal.postings().forEach(posting -> addGraphNode(
          nodes, edges, journalNode, "POSTING:" + posting.id(), "POSTING",
          posting.side() + " " + posting.ledgerAccountCode(), null, posting.createdAt(), false,
          "CONTAINS"));
    });
    outbox.forEach(item -> addGraphNode(
        nodes, edges, root, "OUTBOX:" + item.id(), "OUTBOX", item.eventType(), item.status(),
        item.occurredAt(), "DEAD".equalsIgnoreCase(item.status()), "EMITS"));
    reconciliation.forEach(item -> addGraphNode(
        nodes, edges, root, "RECON:" + item.id(), "RECONCILIATION", item.kind(), null,
        transfer.getUpdatedAt(), isMismatch(item), "CHECKED_BY"));
    audit.forEach(item -> addGraphNode(
        nodes, edges, root, "AUDIT:" + item.id(), "AUDIT", item.action(), null,
        item.occurredAt(), false, "OBSERVED_BY"));

    nodes.sort(Comparator.comparing(CausalNodeResponse::occurredAt,
        Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(CausalNodeResponse::id));
    String firstAnomaly = nodes.stream().filter(CausalNodeResponse::anomalous)
        .map(CausalNodeResponse::id).findFirst().orElse(null);
    return new CausalGraphResponse(
        List.copyOf(nodes), List.copyOf(edges), firstAnomaly,
        failureSignatureService.create(nodes, edges),
        ledger.available() ? "DURABLE_COMPLETE" : "DURABLE_PARTIAL");
  }

  private void addGraphNode(
      List<CausalNodeResponse> nodes,
      List<CausalEdgeResponse> edges,
      String parent,
      String id,
      String type,
      String label,
      String status,
      Instant occurredAt,
      boolean anomalous,
      String relation) {
    nodes.add(new CausalNodeResponse(id, type, label, status, occurredAt, anomalous));
    edges.add(new CausalEdgeResponse(parent + "->" + id, parent, id, relation));
  }

  private boolean isMismatch(ReconciliationEvidenceResponse item) {
    return item.expectedAmount() != null && item.actualAmount() != null
        && item.expectedAmount().compareTo(item.actualAmount()) != 0;
  }

  private boolean isOneOf(String value, String... expected) {
    if (value == null) {
      return false;
    }
    String normalized = value.toUpperCase(Locale.ROOT);
    for (String candidate : expected) {
      if (candidate.equals(normalized)) {
        return true;
      }
    }
    return false;
  }
}
