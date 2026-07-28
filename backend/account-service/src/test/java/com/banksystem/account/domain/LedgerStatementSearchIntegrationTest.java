package com.banksystem.account.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.banksystem.account.application.account.query.LedgerStatementQuery;
import com.banksystem.account.domain.entity.account.AccountEntity;
import com.banksystem.account.domain.entity.account.LedgerEntryEntity;
import com.banksystem.account.domain.enums.account.LedgerEntryType;
import com.banksystem.account.domain.repository.account.AccountRepository;
import com.banksystem.account.domain.repository.account.LedgerEntryRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Regression test for the statement search on real Postgres. The original query used
 * {@code (:param IS NULL OR ...)} which fails at runtime with SQLState 42P18 ("could not
 * determine data type of parameter") on untyped NULL binds — invisible to mock-based unit tests.
 * Skipped when Docker is off.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class LedgerStatementSearchIntegrationTest {

  @Container
  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  private static final Instant T1 = Instant.parse("2026-07-20T10:00:00Z");
  private static final Instant T2 = Instant.parse("2026-07-21T10:00:00Z");
  private static final Instant T3 = Instant.parse("2026-07-22T10:00:00Z");

  @Autowired private AccountRepository accountRepository;
  @Autowired private LedgerEntryRepository ledgerEntryRepository;

  private UUID accountId;

  @BeforeEach
  void seed() {
    AccountEntity account = new AccountEntity();
    account.setId(UUID.randomUUID());
    account.setUserId(UUID.randomUUID());
    account.setAccountNumber("1234567890");
    account.setAccountType("PAYMENT");
    account.setCurrency("VND");
    account.setBalance(new BigDecimal("1000.00"));
    account.setStatus("ACTIVE");
    accountRepository.save(account);
    accountId = account.getId();

    ledgerEntryRepository.saveAll(
        List.of(
            entry("DEBIT", "100.00", T1),
            entry("CREDIT", "50.00", T2),
            entry("CREDIT", "30.00", T3)));
  }

  /** The exact production bug path: no filters → sentinel bounds, no NULL binds. */
  @Test
  void searchWithoutFiltersReturnsAllEntries() {
    Page<LedgerEntryEntity> page =
        ledgerEntryRepository.search(
            accountId,
            false,
            "",
            LedgerStatementQuery.EPOCH,
            LedgerStatementQuery.FAR_FUTURE,
            PageRequest.of(0, 20));

    assertEquals(3, page.getTotalElements());
  }

  @Test
  void searchFiltersByEntryType() {
    Page<LedgerEntryEntity> page =
        ledgerEntryRepository.search(
            accountId,
            true,
            "CREDIT",
            LedgerStatementQuery.EPOCH,
            LedgerStatementQuery.FAR_FUTURE,
            PageRequest.of(0, 20));

    assertEquals(2, page.getTotalElements());
  }

  @Test
  void searchDateBoundsAreInclusive() {
    Page<LedgerEntryEntity> page =
        ledgerEntryRepository.search(accountId, false, "", T2, T3, PageRequest.of(0, 20));

    assertEquals(2, page.getTotalElements());
  }

  private LedgerEntryEntity entry(String type, String amount, Instant createdAt) {
    LedgerEntryEntity e = new LedgerEntryEntity();
    e.setId(UUID.randomUUID());
    e.setAccountId(accountId);
    e.setEntryType(type);
    e.setAmount(new BigDecimal(amount));
    e.setReferenceId("ref-" + UUID.randomUUID());
    e.setCreatedAt(createdAt);
    return e;
  }
}
