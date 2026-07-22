package com.banksystem.account.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

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
      String channel
  ) {}
}
