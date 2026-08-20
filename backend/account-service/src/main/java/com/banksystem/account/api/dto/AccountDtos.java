package com.banksystem.account.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

public final class AccountDtos {
  private AccountDtos() {}

  public record OpenAccountRequest(String accountType) {}

  public record CreateCorporateAccountRequest(
      @NotNull UUID commandId,
      @NotNull UUID corporateId,
      @NotNull UUID createdByUserId,
      String accountType,
      String currency
  ) {}

  public record AccountResponse(
      String id,
      String userId,
      String ownerType,
      String ownerId,
      String accountNumber,
      String accountType,
      String currency,
      BigDecimal balance,
      String status,
      Instant createdAt,
      Instant updatedAt
  ) {}

  public record MoneyCommand(
      @NotNull @DecimalMin("0.01") BigDecimal amount,
      @NotBlank @Size(max = 64) String referenceId,
      @Size(max = 255) String description,
      @Size(max = 160) String commandId,
      Boolean allowAutoSweep
  ) {
    public MoneyCommand(
        BigDecimal amount, String referenceId, String description, String commandId) {
      this(amount, referenceId, description, commandId, false);
    }

    public boolean autoSweepAllowed() {
      return Boolean.TRUE.equals(allowAutoSweep);
    }
  }

  public record DebitAgainstHoldCommand(
      @NotNull UUID holdId,
      @NotNull UUID batchId,
      @NotNull MoneyCommand command
  ) {}

  public record CompensateCreditAgainstHoldCommand(
      @NotNull UUID holdId,
      @NotNull UUID batchId,
      @NotNull MoneyCommand command
  ) {}

  public record AccountOwnershipResponse(
      UUID id,
      String accountNumber,
      String ownerType,
      UUID ownerId,
      String status,
      String currency
  ) {}

  public record MoneyResult(String ledgerEntryId, BigDecimal balanceAfter) {}

  public record AdjustmentRequestedEventRequest(
      @NotNull UUID eventId,
      @NotNull UUID proposalId,
      @NotNull UUID caseId,
      int cycle,
      @NotNull UUID targetAccountId,
      @NotBlank String direction,
      @NotNull BigDecimal amount,
      String currency,
      @NotBlank String referenceId,
      String reason
  ) {}

  /** Single ledger line for account statement (not transfer-order history). */
  public record LedgerEntryResponse(
      String id,
      String accountId,
      String entryType,
      BigDecimal amount,
      /** Signed amount: CREDIT +, DEBIT - */
      BigDecimal signedAmount,
      String referenceId,
      String description,
      Instant createdAt
  ) {}

  public record TopUpRequest(
      @NotNull @DecimalMin("0.01") BigDecimal amount,
      String description
  ) {}

  public record TopUpResponse(
      String accountId,
      String accountNumber,
      String ledgerEntryId,
      String referenceId,
      BigDecimal amount,
      BigDecimal balanceAfter,
      Instant timestamp
  ) {}

  public record AdminAccountFilterRequest(
      Integer page,
      Integer size,
      String q,
      String status,
      String accountType,
      Boolean noCount
  ) {
    public AdminAccountFilterRequest {
      if (page == null) page = 0;
      if (size == null) size = 20;
      if (noCount == null) noCount = false;
    }
  }

  /** Internal reconciliation lookup: ledger entries for a batch of reference ids. */
  public record LedgerSearchRequest(@NotNull @Size(min = 1, max = 2000) List<String> referenceIds) {}

  public record InternalLedgerEntryResponse(
      String id,
      String accountId,
      String entryType,
      BigDecimal amount,
      String referenceId,
      Instant createdAt
  ) {}

  public record StatementFilterRequest(
      Integer page,
      Integer size,
      String entryType,
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
  ) {}

  public record InternalAccountCountsResponse(long total, long frozen) {}
}
