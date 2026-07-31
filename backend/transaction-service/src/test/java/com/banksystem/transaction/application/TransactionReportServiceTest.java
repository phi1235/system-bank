package com.banksystem.transaction.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.api.dto.ReportDtos.ReportSummaryRow;
import com.banksystem.transaction.api.dto.ReportDtos.TransactionReportResponse;
import com.banksystem.transaction.infrastructure.mybatis.TransactionReportMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransactionReportServiceTest {

  private static final ZoneId ZONE = ZoneId.of("Asia/Bangkok");
  // 2026-07-26 10:00 in Asia/Bangkok (UTC+7)
  private static final Instant NOW = Instant.parse("2026-07-26T03:00:00Z");

  private TransactionReportMapper mapper;
  private TransactionReportService service;

  @BeforeEach
  void setUp() {
    mapper = mock(TransactionReportMapper.class);
    service =
        new TransactionReportService(mapper, Clock.fixed(NOW, ZoneOffset.UTC), ZONE.getId());
    when(mapper.summary(any(), any(), any())).thenReturn(emptySummary());
    when(mapper.dailyVolume(any(), any(), any(), any())).thenReturn(List.of());
    when(mapper.statusBreakdown(any(), any(), any())).thenReturn(List.of());
    when(mapper.topSourceAccounts(any(), any(), anyInt())).thenReturn(List.of());
  }

  @Test
  void defaultsToLast30BankingDaysEndingToday() {
    TransactionReportResponse res = service.report(null, null, null, null);

    assertEquals(LocalDate.of(2026, 7, 26), res.to());
    assertEquals(LocalDate.of(2026, 6, 27), res.from());
    assertEquals(ZONE.getId(), res.zone());
    // 2026-06-27 00:00 Asia/Bangkok = 2026-06-26 17:00 UTC; to is exclusive next midnight.
    verify(mapper)
        .summary(
            eq(Instant.parse("2026-06-26T17:00:00Z")),
            eq(Instant.parse("2026-07-26T17:00:00Z")),
            isNull());
    verify(mapper).topSourceAccounts(any(), any(), eq(TransactionReportService.DEFAULT_TOP));
  }

  @Test
  void computesSuccessRateFromSummary() {
    when(mapper.summary(any(), any(), any()))
        .thenReturn(
            new ReportSummaryRow(
                10, 8, 2, new BigDecimal("800.00"), new BigDecimal("8.00"),
                new BigDecimal("100.00")));

    TransactionReportResponse res = service.report(null, null, null, null);

    assertEquals(0.8, res.successRate(), 1e-9);
    assertEquals(10, res.totalCount());
    assertEquals(new BigDecimal("800.00"), res.completedAmount());
  }

  @Test
  void successRateZeroWhenNoTransfers() {
    assertEquals(0.0, service.report(null, null, null, null).successRate(), 1e-9);
  }

  @Test
  void rejectsFromAfterTo() {
    BusinessException ex =
        assertThrows(
            BusinessException.class,
            () ->
                service.report(
                    LocalDate.of(2026, 7, 2), LocalDate.of(2026, 7, 1), null, null));
    assertEquals("REPORT_INVALID_RANGE", ex.getCode());
  }

  @Test
  void rejectsRangeOverOneYear() {
    BusinessException ex =
        assertThrows(
            BusinessException.class,
            () ->
                service.report(
                    LocalDate.of(2025, 1, 1), LocalDate.of(2026, 7, 1), null, null));
    assertEquals("REPORT_RANGE_TOO_LARGE", ex.getCode());
  }

  @Test
  void rejectsMalformedAccountId() {
    BusinessException ex =
        assertThrows(
            BusinessException.class, () -> service.report(null, null, "not-a-uuid", null));
    assertEquals("REPORT_INVALID_ACCOUNT_ID", ex.getCode());
  }

  @Test
  void accountScopedReportSkipsTopRankingAndPassesAccountToQueries() {
    String accountIdStr = UUID.randomUUID().toString();
    UUID accountId = UUID.fromString(accountIdStr);

    TransactionReportResponse res = service.report(null, null, accountIdStr, null);

    assertTrue(res.topSourceAccounts().isEmpty());
    verify(mapper, never()).topSourceAccounts(any(), any(), anyInt());
    verify(mapper).summary(any(), any(), eq(accountId));
    verify(mapper).dailyVolume(any(), any(), eq(ZONE.getId()), eq(accountId));
    verify(mapper).statusBreakdown(any(), any(), eq(accountId));
  }

  @Test
  void clampsTopBetweenOneAndMax() {
    service.report(null, null, null, 999);
    verify(mapper).topSourceAccounts(any(), any(), eq(TransactionReportService.MAX_TOP));
  }

  private static ReportSummaryRow emptySummary() {
    return new ReportSummaryRow(0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
  }
}
