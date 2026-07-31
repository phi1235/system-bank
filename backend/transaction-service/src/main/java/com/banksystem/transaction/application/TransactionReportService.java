package com.banksystem.transaction.application;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.api.dto.ReportDtos.DailyVolumePoint;
import com.banksystem.transaction.api.dto.ReportDtos.ExportReportRow;
import com.banksystem.transaction.api.dto.ReportDtos.ReportSummaryRow;
import com.banksystem.transaction.api.dto.ReportDtos.StatusBreakdownRow;
import com.banksystem.transaction.api.dto.ReportDtos.TopAccountRow;
import com.banksystem.transaction.api.dto.ReportDtos.TransactionReportResponse;
import com.banksystem.transaction.infrastructure.mybatis.TransactionReportMapper;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.cursor.Cursor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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
  public TransactionReportResponse report(
      LocalDate from, LocalDate to, String accountId, Integer top) {
    LocalDate today = LocalDate.now(clock.withZone(reportZone));
    LocalDate effectiveTo = to != null ? to : today;
    LocalDate effectiveFrom = from != null ? from : effectiveTo.minusDays(DEFAULT_RANGE_DAYS - 1L);
    if (effectiveFrom.isAfter(effectiveTo)) {
      throw new BusinessException(
          "REPORT_INVALID_RANGE", "'from' must be on or before 'to'", HttpStatus.BAD_REQUEST);
    }
    if (ChronoUnit.DAYS.between(effectiveFrom, effectiveTo) + 1 > MAX_RANGE_DAYS) {
      throw new BusinessException(
          "REPORT_RANGE_TOO_LARGE",
          "Range must not exceed " + MAX_RANGE_DAYS + " days",
          HttpStatus.BAD_REQUEST);
    }
    UUID fromAccountId = normalizeAccountId(accountId);
    int limit = top == null ? DEFAULT_TOP : Math.min(Math.max(top, 1), MAX_TOP);

    // Half-open instant range covering whole banking days: [from 00:00, to+1 00:00).
    Instant fromTs = effectiveFrom.atStartOfDay(reportZone).toInstant();
    Instant toTs = effectiveTo.plusDays(1).atStartOfDay(reportZone).toInstant();

    ReportSummaryRow summary = mapper.summary(fromTs, toTs, fromAccountId);
    List<DailyVolumePoint> daily = mapper.dailyVolume(fromTs, toTs, reportZone.getId(), fromAccountId);
    List<StatusBreakdownRow> byStatus = mapper.statusBreakdown(fromTs, toTs, fromAccountId);
    // Ranking is meaningless when the report is already scoped to one source account.
    List<TopAccountRow> topAccounts =
        fromAccountId == null ? mapper.topSourceAccounts(fromTs, toTs, limit) : List.of();

    double successRate =
        summary.totalCount() == 0 ? 0.0 : (double) summary.completedCount() / summary.totalCount();

    return new TransactionReportResponse(
        effectiveFrom,
        effectiveTo,
        reportZone.getId(),
        summary.totalCount(),
        summary.completedCount(),
        summary.failedCount(),
        successRate,
        summary.completedAmount(),
        summary.feeAmount(),
        summary.avgCompletedAmount(),
        daily,
        byStatus,
        topAccounts);
  }

  @Transactional(readOnly = true)
  public void exportCsvStream(
      LocalDate from, LocalDate to, String accountId, OutputStream outputStream) {
    LocalDate today = LocalDate.now(clock.withZone(reportZone));
    LocalDate effectiveTo = to != null ? to : today;
    LocalDate effectiveFrom = from != null ? from : effectiveTo.minusDays(DEFAULT_RANGE_DAYS - 1L);
    if (effectiveFrom.isAfter(effectiveTo)) {
      throw new BusinessException(
          "REPORT_INVALID_RANGE", "'from' must be on or before 'to'", HttpStatus.BAD_REQUEST);
    }
    if (ChronoUnit.DAYS.between(effectiveFrom, effectiveTo) + 1 > MAX_RANGE_DAYS) {
      throw new BusinessException(
          "REPORT_RANGE_TOO_LARGE",
          "Range must not exceed " + MAX_RANGE_DAYS + " days",
          HttpStatus.BAD_REQUEST);
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
          "REPORT_EXPORT_FAILED", "Failed to stream CSV report", HttpStatus.INTERNAL_SERVER_ERROR);
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
          "REPORT_INVALID_ACCOUNT_ID", "accountId must be a UUID", HttpStatus.BAD_REQUEST);
    }
  }
}
