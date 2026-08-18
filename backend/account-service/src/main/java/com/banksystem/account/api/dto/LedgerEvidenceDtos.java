package com.banksystem.account.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class LedgerEvidenceDtos {
  private LedgerEvidenceDtos() {}

  public record FinancialEvidenceSearchRequest(
      @NotEmpty @Size(max = 100) List<@NotBlank @Size(max = 100) String> referenceIds) {}

  public record ReverseJournalRequest(
      @NotBlank @Size(max = 160) String commandId,
      @NotBlank @Size(max = 255) String reason) {}

  public record PostingEvidenceResponse(
      String id,
      String accountId,
      String ledgerAccountCode,
      String side,
      BigDecimal amount,
      String currency,
      Instant createdAt) {}

  public record JournalEvidenceResponse(
      String id,
      String businessCommandId,
      String businessReference,
      String transactionId,
      String journalType,
      String status,
      String currency,
      String description,
      String reversalOfJournalId,
      int sequenceNo,
      Instant createdAt,
      Instant postedAt,
      List<PostingEvidenceResponse> postings) {}

  public record LegacyEntryEvidenceResponse(
      String id,
      String accountId,
      String entryType,
      BigDecimal amount,
      String referenceId,
      String description,
      Instant createdAt) {}

  public record FinancialEvidenceSearchResponse(
      List<JournalEvidenceResponse> journals,
      List<LegacyEntryEvidenceResponse> compatibilityEntries,
      String completeness) {}

  public record AccountStateEvidenceResponse(
      String accountId,
      String currency,
      BigDecimal ledgerBalance,
      BigDecimal activeHoldAmount,
      BigDecimal availableBalance,
      Instant at,
      String completeness) {}

  public record HoldEvidenceResponse(
      String id,
      String accountId,
      String transactionId,
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
      String transactionId,
      Instant occurredAt,
      Map<String, Object> payload,
      String payloadSha256) {}

  public record TransactionLedgerEvidenceResponse(
      String transactionId,
      List<JournalEvidenceResponse> journals,
      List<HoldEvidenceResponse> holds,
      List<FinancialEventEvidenceResponse> events,
      String completeness) {}
}
