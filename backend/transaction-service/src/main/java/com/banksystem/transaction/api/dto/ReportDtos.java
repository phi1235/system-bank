package com.banksystem.transaction.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;

/** Admin transaction report read model (MyBatis-backed, see mybatis/TransactionReportMapper.xml). */
public final class ReportDtos {

  private ReportDtos() {}

  public record TransactionReportFilterRequest(
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      String accountId,
      Integer top
  ) {}

  /** One banking-day bucket; days are grouped in the report zone, not UTC. */
  public record DailyVolumePoint(
      LocalDate day,
      long totalCount,
      long completedCount,
      long failedCount,
      BigDecimal completedAmount,
      BigDecimal feeAmount) {}

  public record StatusBreakdownRow(String status, long count, BigDecimal totalAmount) {}

  /** Top source accounts by completed outgoing volume. */
  public record TopAccountRow(String fromAccountId, long transferCount, BigDecimal totalAmount) {}

  /** Whole-range aggregates; failed = FAILED + COMPENSATED. */
  public record ReportSummaryRow(
      long totalCount,
      long completedCount,
      long failedCount,
      BigDecimal completedAmount,
      BigDecimal feeAmount,
      BigDecimal avgCompletedAmount) {}

  public record ExportReportRow(
      String id,
      String createdAt,
      String fromAccountId,
      String toAccountNumber,
      BigDecimal amount,
      BigDecimal feeAmount,
      String currency,
      String status,
      String description) {}

  public record TransactionReportResponse(
      LocalDate from,
      LocalDate to,
      String zone,
      long totalCount,
      long completedCount,
      long failedCount,
      double successRate,
      BigDecimal completedAmount,
      BigDecimal feeAmount,
      BigDecimal avgCompletedAmount,
      List<DailyVolumePoint> daily,
      List<StatusBreakdownRow> byStatus,
      List<TopAccountRow> topSourceAccounts) {}
}
