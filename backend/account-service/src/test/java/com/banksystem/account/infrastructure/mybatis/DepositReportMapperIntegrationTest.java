package com.banksystem.account.infrastructure.mybatis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.banksystem.account.api.dto.deposit.DepositDtos.DepositTenorRow;
import com.banksystem.account.api.dto.deposit.DepositDtos.DepositTotalsRow;
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
 * Funding-summary SQL on real Postgres (FILTER aggregates, LEFT JOIN keeping empty tenors,
 * date window). Skipped when Docker is off.
 */
@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class DepositReportMapperIntegrationTest {

  @Container
  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  private static final LocalDate TODAY = LocalDate.of(2026, 7, 27);
  private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");

  @Autowired private DepositReportMapper mapper;
  @Autowired private DataSource dataSource;

  private UUID accountId;

  @BeforeEach
  void seed() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    accountId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO accounts (id, user_id, account_number, account_type, currency, balance, status)
        VALUES (?, ?, ?, 'PAYMENT', 'VND', 0, 'ACTIVE')
        """,
        accountId, USER, "55" + String.valueOf(System.nanoTime()).substring(0, 8));

    insertDeposit("TD1M", "OPEN", "2000000", "2026-07-30");   // due within 7 days of TODAY
    insertDeposit("TD6M", "OPEN", "5000000", "2026-12-30");
    insertDeposit("TD6M", "MATURED", "3000000", "2026-07-01");
    insertDeposit("TD12M", "CLOSED_EARLY", "4000000", "2027-07-01");
  }

  @Test
  void totalsAggregateByStatusAndDueWindow() {
    DepositTotalsRow totals = mapper.totals(TODAY);

    assertEquals(2, totals.openCount());
    assertEquals(0, totals.openPrincipal().compareTo(new BigDecimal("7000000")));
    assertEquals(1, totals.dueIn7Days());
    assertEquals(1, totals.maturedCount());
    assertEquals(1, totals.closedEarlyCount());
  }

  @Test
  void byProductKeepsEmptyTenorsVisibleAndOrdersByTenor() {
    List<DepositTenorRow> rows = mapper.byProduct();

    assertEquals(4, rows.size());
    assertEquals(List.of(1, 3, 6, 12), rows.stream().map(DepositTenorRow::tenorMonths).toList());
    // TD3M has zero deposits but must still appear
    DepositTenorRow td3m = rows.get(1);
    assertEquals("TD3M", td3m.code());
    assertEquals(0, td3m.openCount());
    // TD6M: one OPEN (5M) + one MATURED (excluded from open aggregates)
    DepositTenorRow td6m = rows.get(2);
    assertEquals(1, td6m.openCount());
    assertEquals(0, td6m.openPrincipal().compareTo(new BigDecimal("5000000")));
  }

  private void insertDeposit(String product, String status, String amount, String maturity) {
    new JdbcTemplate(dataSource)
        .update(
            """
            INSERT INTO term_deposits
              (id, user_id, source_account_id, product_code, amount, rate_bps, early_rate_bps,
               opened_at, maturity_date, status, accrued_interest)
            VALUES (?, ?, ?, ?, ?, 460, 50, ?, ?::date, ?, 0)
            """,
            UUID.randomUUID(), USER, accountId, product, new BigDecimal(amount),
            Timestamp.from(Instant.parse("2026-07-01T03:00:00Z")), maturity, status);
  }
}
