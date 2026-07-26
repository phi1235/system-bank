package com.banksystem.account.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Term-deposit (so tiet kiem) API payloads. */
public final class DepositDtos {

  private DepositDtos() {}

  public record DepositProductResponse(
      String code,
      int tenorMonths,
      int rateBps,
      int earlyRateBps,
      BigDecimal minAmount) {}

  public record DepositQuoteResponse(
      String productCode,
      int tenorMonths,
      int rateBps,
      BigDecimal amount,
      LocalDate openDate,
      LocalDate maturityDate,
      long days,
      BigDecimal expectedInterest,
      BigDecimal totalAtMaturity) {}

  public record OpenDepositRequest(
      @NotNull UUID sourceAccountId,
      @NotBlank String productCode,
      @NotNull @DecimalMin("0.01") BigDecimal amount) {}

  public record TermDepositResponse(
      String id,
      String sourceAccountId,
      String productCode,
      int tenorMonths,
      BigDecimal amount,
      int rateBps,
      int earlyRateBps,
      Instant openedAt,
      LocalDate maturityDate,
      String status,
      /** Interest projected at maturity for OPEN deposits; actual paid interest once closed. */
      BigDecimal interest,
      Instant closedAt) {}
}
