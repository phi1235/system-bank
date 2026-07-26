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
