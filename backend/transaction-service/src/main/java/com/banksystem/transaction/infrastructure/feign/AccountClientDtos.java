package com.banksystem.transaction.infrastructure.feign;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AccountClientDtos {
  private AccountClientDtos() {}

  public record AccountView(
      String id,
      String userId,
      String ownerType,
      String ownerId,
      String accountNumber,
      String accountType,
      String currency,
      BigDecimal balance,
      String status
  ) {
    public UUID idUuid() {
      return UUID.fromString(id);
    }

    public UUID userIdUuid() {
      return userId != null ? UUID.fromString(userId) : null;
    }

    public UUID ownerIdUuid() {
      return ownerId != null ? UUID.fromString(ownerId) : null;
    }

    public boolean isCorporate() {
      return "CORPORATE".equalsIgnoreCase(ownerType);
    }
  }

  public record MoneyCommand(
      BigDecimal amount,
      String referenceId,
      String description,
      String commandId,
      Boolean allowAutoSweep
  ) {
    public MoneyCommand(
        BigDecimal amount, String referenceId, String description, String commandId) {
      this(amount, referenceId, description, commandId, false);
    }
  }

  public record DebitAgainstHoldCommand(
      UUID holdId,
      UUID batchId,
      MoneyCommand command
  ) {}

  public record CompensateCreditAgainstHoldCommand(
      UUID holdId,
      UUID batchId,
      MoneyCommand command
  ) {}

  public record MoneyResult(String ledgerEntryId, BigDecimal balanceAfter) {}

  public record AdjustmentRequestedEventRequest(
      UUID eventId,
      UUID proposalId,
      UUID caseId,
      int cycle,
      UUID targetAccountId,
      String direction,
      BigDecimal amount,
      String currency,
      String referenceId,
      String reason
  ) {}

  public record CreateHoldCommand(
      UUID transactionId,
      String commandId,
      BigDecimal amount,
      String currency,
      Instant expiresAt
  ) {}

  public record LedgerPostingView(
      String id,
      String accountId,
      String ledgerAccountCode,
      String side,
      BigDecimal amount,
      String currency,
      Instant createdAt) {}

  public record LedgerJournalView(
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
      List<LedgerPostingView> postings) {}

  public record AccountHoldView(
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

  public record FinancialEventView(
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

  public record TransactionLedgerEvidenceView(
      String transactionId,
      List<LedgerJournalView> journals,
      List<AccountHoldView> holds,
      List<FinancialEventView> events,
      String completeness) {}

  public record AccountStateEvidenceView(
      String accountId,
      String currency,
      BigDecimal ledgerBalance,
      BigDecimal activeHoldAmount,
      BigDecimal availableBalance,
      Instant at,
      String completeness) {}
}
