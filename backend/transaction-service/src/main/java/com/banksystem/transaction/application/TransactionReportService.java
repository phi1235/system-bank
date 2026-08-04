package com.banksystem.transaction.application;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.api.dto.ReportDtos.DailyVolumePoint;
import com.banksystem.transaction.api.dto.ReportDtos.ExportReportRow;
import com.banksystem.transaction.api.dto.ReportDtos.StatusBreakdownRow;
import com.banksystem.transaction.api.dto.ReportDtos.TopAccountRow;
import com.banksystem.transaction.api.dto.ReportDtos.TransactionReportFilterRequest;
import com.banksystem.transaction.api.dto.ReportDtos.TransactionReportResponse;
import com.banksystem.transaction.infrastructure.mybatis.DailyStatusRow;
import com.banksystem.transaction.infrastructure.mybatis.TransactionReportMapper;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.ibatis.cursor.Cursor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Staff transaction report: summary + daily series + status breakdown + top source accounts.
 * Days are banking days in the configured zone (same zone as the daily transfer limit).
 */
@Service
public class TransactionReportService {

  static final int MAX_RANGE_DAYS = 366;
  static final int DEFAULT_RANGE_DAYS = 30;
  static final int DEFAULT_TOP = 5;
  static final int MAX_TOP = 20;

  private final TransactionReportMapper mapper;
  private final Clock clock;
  private final ZoneId reportZone;

  public TransactionReportService(
      TransactionReportMapper mapper,
      Clock clock,
      @Value("${bank.transfer.daily-limit-zone}") String reportZone) {
    this.mapper = mapper;
    this.clock = clock;
    this.reportZone = ZoneId.of(reportZone);
  }

  @Transactional(readOnly = true)
  public TransactionReportResponse report(TransactionReportFilterRequest req) {
    return report(req.from(), req.to(), req.accountId(), req.top());
  }

  @Transactional(readOnly = true)
  public TransactionReportResponse report(
      LocalDate from, LocalDate to, String accountId, Integer top) {
    LocalDate today = LocalDate.now(clock.withZone(reportZone));
    LocalDate effectiveTo = to != null ? to : today;
    LocalDate effectiveFrom = from != null ? from : effectiveTo.minusDays(DEFAULT_RANGE_DAYS - 1L);
    if (effectiveFrom.isAfter(effectiveTo)) {
      throw new BusinessException(
          "REPORT_INVALID_RANGE", "'from' must be on or before 'to'");
    }
    if (ChronoUnit.DAYS.between(effectiveFrom, effectiveTo) + 1 > MAX_RANGE_DAYS) {
      throw new BusinessException(
          "REPORT_RANGE_TOO_LARGE",
          "Range must not exceed " + MAX_RANGE_DAYS + " days");
    }
    UUID fromAccountId = normalizeAccountId(accountId);
    int limit = top == null ? DEFAULT_TOP : Math.min(Math.max(top, 1), MAX_TOP);

    // Half-open instant range covering whole banking days: [from 00:00, to+1 00:00).
    Instant fromTs = effectiveFrom.atStartOfDay(reportZone).toInstant();
    Instant toTs = effectiveTo.plusDays(1).atStartOfDay(reportZone).toInstant();

    // Single-scan: combined GROUP BY (day, status) replaces 3 separate full-table scans.
    var fCombined = CompletableFuture.supplyAsync(
        () -> mapper.dailyStatusAgg(fromTs, toTs, reportZone.getId(), fromAccountId));
    var fTop = CompletableFuture.supplyAsync(
        () -> fromAccountId == null ? mapper.topSourceAccounts(fromTs, toTs, limit) : List.<TopAccountRow>of());

    CompletableFuture.allOf(fCombined, fTop).join();

    List<DailyStatusRow> combinedRows = fCombined.join();
    List<TopAccountRow> topAccounts = fTop.join();

    // Derive summary, dailyVolume, and statusBreakdown from combined rows in Java (O(rows) in memory).
    long totalCount = 0, completedCount = 0, failedCount = 0;
    BigDecimal completedAmount = BigDecimal.ZERO;
    BigDecimal feeAmount = BigDecimal.ZERO;
    Map<String, long[]> statusAgg = new LinkedHashMap<>();        // status → [count, 0] + amount
    Map<String, BigDecimal> statusAmountAgg = new LinkedHashMap<>();
    Map<LocalDate, long[]> dailyAgg = new TreeMap<>();            // day → [total, completed, failed]
    Map<LocalDate, BigDecimal[]> dailyAmountAgg = new TreeMap<>(); // day → [completedAmt, feeAmt]

    for (DailyStatusRow r : combinedRows) {
      totalCount += r.cnt();
      boolean isCompleted = "COMPLETED".equals(r.status());
      boolean isFailed = "FAILED".equals(r.status()) || "COMPENSATED".equals(r.status());
      if (isCompleted) {
        completedCount += r.cnt();
        completedAmount = completedAmount.add(r.totalAmount());
        feeAmount = feeAmount.add(r.totalFee());
      }
      if (isFailed) {
        failedCount += r.cnt();
      }

      // Status breakdown
      statusAgg.merge(r.status(), new long[]{r.cnt()}, (a, b) -> { a[0] += b[0]; return a; });
      statusAmountAgg.merge(r.status(), r.totalAmount(), BigDecimal::add);

      // Daily volume
      dailyAgg.merge(r.day(), new long[]{r.cnt(), isCompleted ? r.cnt() : 0, isFailed ? r.cnt() : 0},
          (a, b) -> { a[0] += b[0]; a[1] += b[1]; a[2] += b[2]; return a; });
      dailyAmountAgg.merge(r.day(),
          new BigDecimal[]{isCompleted ? r.totalAmount() : BigDecimal.ZERO, isCompleted ? r.totalFee() : BigDecimal.ZERO},
          (a, b) -> { a[0] = a[0].add(b[0]); a[1] = a[1].add(b[1]); return a; });
    }

    double successRate = totalCount == 0 ? 0.0 : (double) completedCount / totalCount;
    BigDecimal avgCompletedAmount = completedCount == 0 ? BigDecimal.ZERO
        : completedAmount.divide(BigDecimal.valueOf(completedCount), 2, java.math.RoundingMode.HALF_UP);

    List<DailyVolumePoint> daily = dailyAgg.entrySet().stream()
        .map(e -> {
          BigDecimal[] amounts = dailyAmountAgg.getOrDefault(e.getKey(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
          return new DailyVolumePoint(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2], amounts[0], amounts[1]);
        })
        .toList();

    List<StatusBreakdownRow> byStatus = statusAgg.entrySet().stream()
        .map(e -> new StatusBreakdownRow(e.getKey(), e.getValue()[0], statusAmountAgg.getOrDefault(e.getKey(), BigDecimal.ZERO)))
        .sorted((a, b) -> Long.compare(b.count(), a.count()))
        .toList();

    return new TransactionReportResponse(
        effectiveFrom,
        effectiveTo,
        reportZone.getId(),
        totalCount,
        completedCount,
        failedCount,
        successRate,
        completedAmount,
        feeAmount,
        avgCompletedAmount,
        daily,
        byStatus,
        topAccounts);
  }

  @Transactional(readOnly = true)
  public void exportCsvStream(TransactionReportFilterRequest req, OutputStream outputStream) {
    exportCsvStream(req.from(), req.to(), req.accountId(), outputStream);
  }

  @Transactional(readOnly = true)
  public void exportCsvStream(
      LocalDate from, LocalDate to, String accountId, OutputStream outputStream) {
    LocalDate today = LocalDate.now(clock.withZone(reportZone));
    LocalDate effectiveTo = to != null ? to : today;
    LocalDate effectiveFrom = from != null ? from : effectiveTo.minusDays(DEFAULT_RANGE_DAYS - 1L);
    if (effectiveFrom.isAfter(effectiveTo)) {
      throw new BusinessException(
          "REPORT_INVALID_RANGE", "'from' must be on or before 'to'");
    }
    if (ChronoUnit.DAYS.between(effectiveFrom, effectiveTo) + 1 > MAX_RANGE_DAYS) {
      throw new BusinessException(
          "REPORT_RANGE_TOO_LARGE",
          "Range must not exceed " + MAX_RANGE_DAYS + " days");
    }
    UUID fromAccountId = normalizeAccountId(accountId);

    Instant fromTs = effectiveFrom.atStartOfDay(reportZone).toInstant();
    Instant toTs = effectiveTo.plusDays(1).atStartOfDay(reportZone).toInstant();

    try {
      // Write UTF-8 BOM for Excel compatibility
      outputStream.write(0xEF);
      outputStream.write(0xBB);
      outputStream.write(0xBF);

      BufferedWriter writer =
          new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));

      writer.write(
          "\"ID\",\"Created At (UTC)\",\"From Account ID\",\"To Account Number\",\"Amount\",\"Fee Amount\",\"Currency\",\"Status\",\"Description\"\n");

      int count = 0;
      try (Cursor<ExportReportRow> cursor = mapper.streamExport(fromTs, toTs, fromAccountId)) {
        for (ExportReportRow row : cursor) {
          String desc = row.description() != null ? row.description().replace("\"", "\"\"") : "";
          writer.write(
              String.format(
                  "\"%s\",\"%s\",\"%s\",\"%s\",%s,%s,\"%s\",\"%s\",\"%s\"\n",
                  row.id(),
                  row.createdAt(),
                  row.fromAccountId(),
                  row.toAccountNumber(),
                  row.amount() != null ? row.amount().toPlainString() : "0",
                  row.feeAmount() != null ? row.feeAmount().toPlainString() : "0",
                  row.currency(),
                  row.status(),
                  desc));
          count++;
          if (count % 1000 == 0) {
            writer.flush();
          }
        }
      }
      writer.flush();
    } catch (IOException e) {
      throw new BusinessException(
          "REPORT_EXPORT_FAILED", "Failed to stream CSV report");
    }
  }

  private UUID normalizeAccountId(String accountId) {
    if (accountId == null || accountId.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(accountId.trim());
    } catch (IllegalArgumentException e) {
      throw new BusinessException(
          "REPORT_INVALID_ACCOUNT_ID", "accountId must be a UUID");
    }
  }
}
