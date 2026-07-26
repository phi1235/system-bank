package com.banksystem.account.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * V3 migration on real Postgres: product seed present, contract round-trip, entity ↔ schema
 * validated by {@code ddl-auto: validate} at context start. Skipped when Docker is off.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class TermDepositPersistenceIntegrationTest {

  @Container
  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private DepositProductRepository productRepository;
  @Autowired private TermDepositRepository depositRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private jakarta.persistence.EntityManager entityManager;

  @Test
  void migrationSeedsFourActiveProductsOrderedByTenor() {
    List<DepositProductEntity> products = productRepository.findByActiveTrueOrderByTenorMonthsAsc();

    assertEquals(4, products.size());
    assertEquals(List.of(1, 3, 6, 12),
        products.stream().map(DepositProductEntity::getTenorMonths).toList());
    assertTrue(products.stream().allMatch(p -> p.getMinAmount().signum() > 0));
  }

  @Test
  void termDepositRoundTripAndUserOrdering() {
    UUID userId = UUID.randomUUID();
    AccountEntity account = new AccountEntity();
    account.setId(UUID.randomUUID());
    account.setUserId(userId);
    account.setAccountNumber("1234509876");
    account.setAccountType("PAYMENT");
    account.setCurrency("VND");
    account.setBalance(new BigDecimal("0.00"));
    account.setStatus("ACTIVE");
    accountRepository.save(account);

    depositRepository.saveAll(
        List.of(
            deposit(userId, account.getId(), Instant.parse("2026-07-01T03:00:00Z")),
            deposit(userId, account.getId(), Instant.parse("2026-07-20T03:00:00Z"))));

    List<TermDepositEntity> mine = depositRepository.findByUserIdOrderByOpenedAtDesc(userId);
    assertEquals(2, mine.size());
    assertTrue(mine.get(0).getOpenedAt().isAfter(mine.get(1).getOpenedAt()));
    assertEquals(TermDepositStatus.OPEN, mine.get(0).getStatus());
  }

  /** Flag+sentinel admin search on real Postgres (the 42P18-safe pattern). */
  @Test
  void adminSearchFiltersByFlagsWithoutNullBinds() {
    UUID userA = UUID.randomUUID();
    UUID userB = UUID.randomUUID();
    AccountEntity account = new AccountEntity();
    account.setId(UUID.randomUUID());
    account.setUserId(userA);
    account.setAccountNumber("1234509878");
    account.setAccountType("PAYMENT");
    account.setCurrency("VND");
    account.setBalance(new BigDecimal("0.00"));
    account.setStatus("ACTIVE");
    accountRepository.save(account);

    TermDepositEntity d1 = deposit(userA, account.getId(), Instant.parse("2026-07-01T03:00:00Z"));
    TermDepositEntity d2 = deposit(userB, account.getId(), Instant.parse("2026-07-02T03:00:00Z"));
    d2.setProductCode("TD1M");
    TermDepositEntity d3 = deposit(userA, account.getId(), Instant.parse("2026-07-03T03:00:00Z"));
    d3.setStatus(TermDepositStatus.CLOSED_EARLY);
    depositRepository.saveAll(List.of(d1, d2, d3));

    var page = org.springframework.data.domain.PageRequest.of(0, 20);
    // No filters — every flag false, sentinel bounds (the old NULL-bind crash path)
    assertEquals(3, depositRepository.searchAdmin(
        false, TermDepositStatus.OPEN, false, "", false, new UUID(0, 0), false, new UUID(0, 0),
        LocalDate.of(1970, 1, 1), LocalDate.of(9999, 12, 31), page).getTotalElements());
    // Status filter
    assertEquals(2, depositRepository.searchAdmin(
        true, TermDepositStatus.OPEN, false, "", false, new UUID(0, 0), false, new UUID(0, 0),
        LocalDate.of(1970, 1, 1), LocalDate.of(9999, 12, 31), page).getTotalElements());
    // User + product filters combined
    assertEquals(1, depositRepository.searchAdmin(
        false, TermDepositStatus.OPEN, true, "TD1M", true, userB, false, new UUID(0, 0),
        LocalDate.of(1970, 1, 1), LocalDate.of(9999, 12, 31), page).getTotalElements());
  }

  /** The set-based accrual UPDATE runs Postgres-only SQL (AT TIME ZONE, date arithmetic). */
  @Test
  void accrualUpdatesOpenDepositsSetBased() {
    UUID userId = UUID.randomUUID();
    AccountEntity account = new AccountEntity();
    account.setId(UUID.randomUUID());
    account.setUserId(userId);
    account.setAccountNumber("1234509877");
    account.setAccountType("PAYMENT");
    account.setCurrency("VND");
    account.setBalance(new BigDecimal("0.00"));
    account.setStatus("ACTIVE");
    accountRepository.save(account);

    // Opened exactly 30 banking days ago (same wall time — date diff is stable).
    TermDepositEntity open =
        deposit(userId, account.getId(), Instant.now().minusSeconds(30L * 24 * 3600));
    open.setAmount(new BigDecimal("10000000.00"));
    open.setRateBps(460);
    TermDepositEntity closed =
        deposit(userId, account.getId(), Instant.now().minusSeconds(30L * 24 * 3600));
    closed.setStatus(TermDepositStatus.CLOSED_EARLY);
    depositRepository.saveAll(List.of(open, closed));
    entityManager.flush();

    int updated = depositRepository.accrueDailyInterest("Asia/Bangkok");
    entityManager.clear();

    assertEquals(1, updated);
    // 10,000,000 * 4.60% * 30/365 = 37,808.22
    assertEquals(
        new BigDecimal("37808.22"),
        depositRepository.findById(open.getId()).orElseThrow().getAccruedInterest());
    // CLOSED_EARLY row untouched
    assertEquals(
        new BigDecimal("0.00"),
        depositRepository.findById(closed.getId()).orElseThrow().getAccruedInterest());
  }

  private TermDepositEntity deposit(UUID userId, UUID accountId, Instant openedAt) {
    TermDepositEntity d = new TermDepositEntity();
    d.setId(UUID.randomUUID());
    d.setUserId(userId);
    d.setSourceAccountId(accountId);
    d.setProductCode("TD6M");
    d.setAmount(new BigDecimal("5000000.00"));
    d.setRateBps(460);
    d.setEarlyRateBps(50);
    d.setOpenedAt(openedAt);
    d.setMaturityDate(LocalDate.of(2027, 1, 26));
    d.setStatus(TermDepositStatus.OPEN);
    return d;
  }
}
