package com.banksystem.account.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class AccountDtos {
  private AccountDtos() {}

  public record OpenAccountRequest(String accountType) {}

  public record AccountResponse(
      String id,
      String userId,
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
      @NotBlank String referenceId,
      String description,
      String commandId
  ) {}

  public record MoneyResult(String ledgerEntryId, BigDecimal balanceAfter) {}

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
      int page,
      int size,
      String q,
      String status,
      String accountType,
      boolean noCount
  ) {}

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
}
