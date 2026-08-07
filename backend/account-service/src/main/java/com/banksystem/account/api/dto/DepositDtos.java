package com.banksystem.account.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

/** Term-deposit (so tiet kiem) API payloads. */
public final class DepositDtos {

  private DepositDtos() {}

  public record DepositProductResponse(
      String code,
      int tenorMonths,
      int rateBps,
      int earlyRateBps,
      BigDecimal minAmount,
      boolean active) {}

  /** Admin partial update; null fields are left unchanged. Contracts keep their snapshots. */
  public record UpdateDepositProductRequest(
      @Min(0) @Max(3000) Integer rateBps,
      @Min(0) @Max(3000) Integer earlyRateBps,
      @DecimalMin("1") BigDecimal minAmount,
      Boolean active) {}

  /** Admin drill-down row: one contract with its owner (name/number enriched for humans). */
  public record AdminTermDepositRow(
      String id,
      String userId,
      String ownerName,
      String sourceAccountId,
      String sourceAccountNumber,
      String productCode,
      int tenorMonths,
      BigDecimal amount,
      int rateBps,
      BigDecimal accruedInterest,
      Instant openedAt,
      LocalDate maturityDate,
      String status,
      Instant closedAt) {}

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

  /** Whole-book funding totals (admin, MyBatis read model). */
  public record DepositTotalsRow(
      long openCount,
      BigDecimal openPrincipal,
      BigDecimal openAccrued,
      long dueIn7Days,
      long maturedCount,
      long closedEarlyCount) {}

  /** Per-product funding breakdown (admin, MyBatis read model). */
  public record DepositTenorRow(
      String code,
      int tenorMonths,
      int rateBps,
      long openCount,
      BigDecimal openPrincipal,
      BigDecimal openAccrued) {}

  public record DepositAdminSummaryResponse(
      DepositTotalsRow totals, List<DepositTenorRow> byProduct) {}

  public record BatchRunResponse(int accruedUpdated, int matured, int failed) {}

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

  public record AdminDepositFilterRequest(
      Integer page,
      Integer size,
      String status,
      String productCode,
      String userId,
      String accountId,
      String accountNumber,
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate maturityFrom,
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate maturityTo
  ) {}
}
