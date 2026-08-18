package com.banksystem.transaction.application.forensics;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Application boundary for immutable ledger evidence owned by account-service. */
public interface LedgerEvidenceGateway {
  Optional<TransactionLedgerEvidence> findByTransactionId(UUID transactionId);
  Optional<AccountStateEvidence> findAccountState(UUID accountId, Instant at);

  record AccountStateEvidence(
      String accountId,
      String currency,
      BigDecimal ledgerBalance,
      BigDecimal activeHoldAmount,
      BigDecimal availableBalance,
      Instant at,
      String completeness) {}

  record PostingEvidence(
      String id,
      String accountId,
      String ledgerAccountCode,
      String side,
      BigDecimal amount,
      String currency,
      Instant createdAt) {}

  record JournalEvidence(
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
      List<PostingEvidence> postings) {}

  record HoldEvidence(
      String id,
      String accountId,
      BigDecimal amount,
      String currency,
      String status,
      Instant expiresAt,
      String capturedJournalId,
      Instant createdAt,
      Instant updatedAt) {}

  record FinancialEventEvidence(
      String eventId,
      String aggregateType,
      String aggregateId,
      long sequenceNo,
      String eventType,
      int schemaVersion,
      Instant occurredAt,
      Map<String, Object> payload,
      String payloadSha256) {}

  record TransactionLedgerEvidence(
      List<JournalEvidence> journals,
      List<HoldEvidence> holds,
      List<FinancialEventEvidence> events,
      String completeness) {}
}
