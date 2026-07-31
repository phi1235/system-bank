package com.banksystem.transaction.infrastructure.mybatis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.banksystem.transaction.api.dto.ReportDtos.DailyVolumePoint;
import com.banksystem.transaction.api.dto.ReportDtos.ReportSummaryRow;
import com.banksystem.transaction.api.dto.ReportDtos.StatusBreakdownRow;
import com.banksystem.transaction.api.dto.ReportDtos.TopAccountRow;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs the hand-written reporting SQL against real Postgres (Flyway-migrated Testcontainer) —
 * the Postgres-only constructs ({@code FILTER}, {@code AT TIME ZONE}, {@code ::uuid}, {@code
 * LIMIT}) cannot be verified by unit tests or the XML parse test. Skipped when Docker is off.
 */
@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class TransactionReportMapperIntegrationTest {

  @Container
  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  private static final String ZONE = "Asia/Bangkok";
  private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID ACCOUNT_A = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
  private static final UUID ACCOUNT_B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");
  private static final UUID DEST = UUID.fromString("cccccccc-0000-0000-0000-000000000001");

  // Banking days 2026-07-14 .. 2026-07-16 in Asia/Bangkok (UTC+7): [2026-07-13T17:00Z, 2026-07-16T17:00Z)
  private static final Instant FROM_TS = Instant.parse("2026-07-13T17:00:00Z");
  private static final Instant TO_TS = Instant.parse("2026-07-16T17:00:00Z");

  @Autowired private TransactionReportMapper mapper;
  @Autowired private DataSource dataSource;

  @BeforeEach
  void seed() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    // day 2026-07-14 (BKK)
    insert(jdbc, ACCOUNT_B, "COMPLETED", "300.00", "0.00", "2026-07-14T10:00:00Z");
    // 2026-07-15 in UTC but 2026-07-16 00:30 in BKK — must land in the 07-16 bucket
    insert(jdbc, ACCOUNT_A, "COMPLETED", "1000.00", "10.00", "2026-07-15T17:30:00Z");
    insert(jdbc, ACCOUNT_B, "COMPLETED", "2000.00", "0.00", "2026-07-16T05:00:00Z");
    insert(jdbc, ACCOUNT_A, "FAILED", "500.00", "0.00", "2026-07-16T08:00:00Z");
    // BKK 23:59:59 — last second inside the range
    insert(jdbc, ACCOUNT_A, "COMPLETED", "700.00", "5.00", "2026-07-16T16:59:59Z");
    // BKK 2026-07-17 00:00 — exactly the exclusive upper bound, must be excluded
    insert(jdbc, ACCOUNT_A, "COMPLETED", "9999.00", "0.00", "2026-07-16T17:00:00Z");
  }

  @Test
  void summaryAggregatesOnlyCompletedMoneyWithinHalfOpenRange() {
    ReportSummaryRow s = mapper.summary(FROM_TS, TO_TS, null);

    assertEquals(5, s.totalCount());
    assertEquals(4, s.completedCount());
    assertEquals(1, s.failedCount());
    assertEquals(0, s.completedAmount().compareTo(new BigDecimal("4000")));
    assertEquals(0, s.feeAmount().compareTo(new BigDecimal("15")));
    assertEquals(0, s.avgCompletedAmount().compareTo(new BigDecimal("1000")));
  }

  @Test
  void dailyVolumeBucketsByBankingDayNotUtc() {
    List<DailyVolumePoint> daily = mapper.dailyVolume(FROM_TS, TO_TS, ZONE, null);

    assertEquals(2, daily.size());
    assertEquals(LocalDate.of(2026, 7, 14), daily.get(0).day());
    assertEquals(1, daily.get(0).totalCount());

    DailyVolumePoint d16 = daily.get(1);
    assertEquals(LocalDate.of(2026, 7, 16), d16.day());
    assertEquals(4, d16.totalCount());
    assertEquals(3, d16.completedCount());
    assertEquals(1, d16.failedCount());
    assertEquals(0, d16.completedAmount().compareTo(new BigDecimal("3700")));
    // No bucket for 07-15: the 17:30Z transfer belongs to banking day 07-16
    assertTrue(daily.stream().noneMatch(d -> d.day().equals(LocalDate.of(2026, 7, 15))));
  }

  @Test
  void summaryFiltersBySourceAccountViaUuidCast() {
    ReportSummaryRow s = mapper.summary(FROM_TS, TO_TS, ACCOUNT_A);

    assertEquals(3, s.totalCount());
    assertEquals(2, s.completedCount());
    assertEquals(0, s.completedAmount().compareTo(new BigDecimal("1700")));
  }

  @Test
  void statusBreakdownOrdersByCountDesc() {
    List<StatusBreakdownRow> rows = mapper.statusBreakdown(FROM_TS, TO_TS, null);

    assertEquals(2, rows.size());
    assertEquals("COMPLETED", rows.get(0).status());
    assertEquals(4, rows.get(0).count());
    assertEquals(0, rows.get(0).totalAmount().compareTo(new BigDecimal("4000")));
    assertEquals("FAILED", rows.get(1).status());
    assertEquals(1, rows.get(1).count());
  }

  @Test
  void topSourceAccountsRanksByCompletedVolumeAndHonorsLimit() {
    List<TopAccountRow> top = mapper.topSourceAccounts(FROM_TS, TO_TS, 5);

    assertEquals(2, top.size());
    assertEquals(ACCOUNT_B.toString(), top.get(0).fromAccountId());
    assertEquals(0, top.get(0).totalAmount().compareTo(new BigDecimal("2300")));
    assertEquals(ACCOUNT_A.toString(), top.get(1).fromAccountId());
    assertEquals(0, top.get(1).totalAmount().compareTo(new BigDecimal("1700")));

    assertEquals(1, mapper.topSourceAccounts(FROM_TS, TO_TS, 1).size());
  }

  private void insert(
      JdbcTemplate jdbc, UUID fromAccount, String status, String amount, String fee, String at) {
    UUID id = UUID.randomUUID();
    Timestamp ts = Timestamp.from(Instant.parse(at));
    jdbc.update(
        """
        INSERT INTO transfer_orders
          (id, idempotency_key, user_id, from_account_id, to_account_id, to_account_number,
           amount, currency, request_fingerprint, status, fee_amount, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, 'VND', ?, ?, ?, ?, ?)
        """,
        id, "idem-" + id, USER, fromAccount, DEST, "0000000001",
        new BigDecimal(amount), "fp-" + id, status, new BigDecimal(fee), ts, ts);
  }
}
