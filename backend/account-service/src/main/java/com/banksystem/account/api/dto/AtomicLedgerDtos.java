package com.banksystem.account.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AtomicLedgerDtos {
  private AtomicLedgerDtos() {}

  public record CollectionReceiptCommand(
      @NotBlank String businessCommandId,
      @NotBlank String businessReference,
      UUID transactionId,
      @NotNull UUID collectionAccountId,
      @NotBlank String clearingAccountCode,
      @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
      @NotBlank String currency,
      String description
  ) {}

  public record SettlementLegCommand(
      UUID accountId,
      @NotBlank String ledgerAccountCode,
      @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
      String description
  ) {}

  public record SettlementPostingCommand(
      @NotBlank String businessCommandId,
      @NotBlank String businessReference,
      UUID transactionId,
      @NotNull UUID sourceAccountId,
      @NotBlank String currency,
      @NotNull @DecimalMin(value = "0.01") BigDecimal grossAmount,
      @NotEmpty List<@Valid SettlementLegCommand> legs,
      String description
  ) {}

  public record PayoutClearingCommand(
      @NotBlank String businessCommandId,
      @NotBlank String businessReference,
      UUID payoutId,
      @NotBlank String payableAccountCode,
      @NotBlank String clearingAccountCode,
      @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
      @NotBlank String currency,
      String description
  ) {}

  public record SettlementReversalCommand(
      @NotBlank String businessCommandId,
      @NotBlank String reason
  ) {}

  public record AtomicPostingResponse(
      UUID journalId,
      String businessCommandId,
      String status,
      String journalType,
      String currency,
      BigDecimal amount,
      Instant postedAt
  ) {}
}
