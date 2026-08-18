package com.banksystem.transaction.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

public final class ForensicDtos {

  private ForensicDtos() {}

  public record ForensicInvestigationFilterRequest(
      String q,
      String transactionId,
      String accountId,
      String transferStatus,
      String riskDecision,
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      @Min(0) Integer page,
      @Min(1) @Max(100) Integer size
  ) {}

  public record EvidenceTimelineFilterRequest(
      String source,
      @Min(0) Integer page,
      @Min(1) @Max(100) Integer size
  ) {}

  public record ForensicsCapabilitiesResponse(boolean enabled) {}

  public record InvestigationItemResponse(
      String transactionId,
      String status,
      String transferType,
      String fromAccountId,
      String toAccountId,
      String toAccountNumber,
      String targetBankCode,
      String targetAccountName,
      BigDecimal amount,
      BigDecimal feeAmount,
      String currency,
      String riskDecision,
      Integer riskScore,
      String providerStatus,
      String failureReason,
      boolean needsAttention,
      String primarySignal,
      Instant createdAt,
      Instant updatedAt
  ) {}

  public record SagaEvidenceResponse(
      String id,
      String step,
      String status,
      String detail,
      Instant occurredAt
  ) {}

  public record OutboxEvidenceResponse(
      String id,
      String eventType,
      String status,
      int attemptCount,
      String lastError,
      Instant occurredAt,
      Instant publishedAt
  ) {}

  public record ReconciliationEvidenceResponse(
      String id,
      String runId,
      String kind,
      String entryRef,
      BigDecimal expectedAmount,
      BigDecimal actualAmount,
      String detail
  ) {}

  public record AuditEvidenceResponse(
      String id,
      String actorUserId,
      String action,
      String resourceType,
      String detail,
      Instant occurredAt
  ) {}

  public record TimelineEvidenceResponse(
      String source,
      String sourceId,
      String event,
      String status,
      String detail,
      Instant occurredAt
  ) {}

  public record LedgerPostingEvidenceResponse(
      String id,
      String accountId,
      String ledgerAccountCode,
      String side,
      BigDecimal amount,
      String currency,
      Instant createdAt) {}

  public record LedgerJournalEvidenceResponse(
      String id,
      String businessCommandId,
      String businessReference,
      String journalType,
      String status,
      String currency,
      String description,
      String reversalOfJournalId,
      int sequenceNo,
      Instant createdAt,
      Instant postedAt,
      List<LedgerPostingEvidenceResponse> postings) {}

  public record LedgerHoldEvidenceResponse(
      String id,
      String accountId,
      BigDecimal amount,
      String currency,
      String status,
      Instant expiresAt,
      String capturedJournalId,
      Instant createdAt,
      Instant updatedAt) {}

  public record FinancialEventEvidenceResponse(
      String eventId,
      String aggregateType,
      String aggregateId,
      long sequenceNo,
      String eventType,
      int schemaVersion,
      Instant occurredAt,
      Map<String, Object> payload,
      String payloadSha256) {}

  public record LedgerEvidenceResponse(
      boolean available,
      String completeness,
      List<LedgerJournalEvidenceResponse> journals,
      List<LedgerHoldEvidenceResponse> holds,
      List<FinancialEventEvidenceResponse> events) {}

  public record FinancialViolationResponse(
      String ruleCode,
      String severity,
      String status,
      String message,
      List<String> evidenceIds) {}

  public record CausalNodeResponse(
      String id,
      String type,
      String label,
      String status,
      Instant occurredAt,
      boolean anomalous) {}

  public record CausalEdgeResponse(
      String id,
      String fromNodeId,
      String toNodeId,
      String relation) {}

  public record CausalGraphResponse(
      List<CausalNodeResponse> nodes,
      List<CausalEdgeResponse> edges,
      String firstAnomalousNodeId,
      String failureSignature,
      String completeness) {}

  public record TemporalAccountStateResponse(
      String accountId,
      String currency,
      BigDecimal ledgerBalance,
      BigDecimal activeHoldAmount,
      BigDecimal availableBalance,
      String completeness) {}

  public record TemporalInvestigationStateResponse(
      String transactionId,
      Instant at,
      String transactionState,
      List<TemporalAccountStateResponse> accountStates,
      List<String> missingSources,
      String completeness) {}

  public record InvestigationDetailResponse(
      InvestigationItemResponse transaction,
      String evidenceCompleteness,
      List<String> missingSources,
      List<SagaEvidenceResponse> sagaSteps,
      List<OutboxEvidenceResponse> outboxEvents,
      List<ReconciliationEvidenceResponse> reconciliationItems,
      List<AuditEvidenceResponse> auditEvents,
      LedgerEvidenceResponse ledgerEvidence,
      List<FinancialViolationResponse> violations,
      CausalGraphResponse causalGraph,
      List<TimelineEvidenceResponse> timeline
  ) {}
}
