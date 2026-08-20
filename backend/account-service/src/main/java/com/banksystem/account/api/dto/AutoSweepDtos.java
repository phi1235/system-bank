package com.banksystem.account.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class AutoSweepDtos {
  private AutoSweepDtos() {}

  public record UpsertAutoSweepRequest(
      @NotBlank @Size(max = 20) String productCode,
      @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2)
      BigDecimal thresholdAmount,
      @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal minSweepAmount,
      @PositiveOrZero Long version) {}

  public record AutoSweepOperationsRequest(
      @Min(1) @Max(100) Integer limit) {}

  public record AutoSweepListRequest(
      @Min(0) Integer page,
      @Min(1) @Max(100) Integer size) {}

  public record SweepProductResponse(
      String code,
      String currency,
      int annualRateBps,
      BigDecimal minThreshold,
      BigDecimal defaultThreshold,
      BigDecimal minSweepAmount,
      BigDecimal maxPositionAmount) {}

  public record AutoSweepProfileResponse(
      UUID id,
      UUID sourceAccountId,
      String sourceAccountNumber,
      String productCode,
      String status,
      BigDecimal thresholdAmount,
      BigDecimal minSweepAmount,
      int annualRateBps,
      BigDecimal casaBalance,
      BigDecimal availableBalance,
      BigDecimal flexiblePrincipal,
      BigDecimal accruedInterest,
      BigDecimal totalLiquidity,
      LocalDate lastSweepBusinessDate,
      long version,
      Instant updatedAt) {}

  public record AutoSweepOperationResponse(
      UUID id,
      String operationType,
      String triggerType,
      BigDecimal amount,
      Integer annualRateBps,
      LocalDate businessDate,
      String paymentReference,
      BigDecimal casaBalanceAfter,
      BigDecimal positionBalanceAfter,
      Instant createdAt) {}

  public record AutoSweepBatchResponse(
      LocalDate businessDate,
      int processed,
      int failed,
      BigDecimal sweptAmount,
      boolean claimed) {}
}
