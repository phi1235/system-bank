package com.banksystem.account.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * V4 cards migration on real Postgres. The partial unique index (one non-CLOSED card per
 * account) can only be verified against Postgres — H2/mocks cannot express it. Skipped when
 * Docker is off.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class CardPersistenceIntegrationTest {

  @Container
  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private AccountRepository accountRepository;
  @Autowired private CardRepository cardRepository;
  @Autowired private jakarta.persistence.EntityManager entityManager;

  private UUID accountId;
  private UUID userId;

  @BeforeEach
  void seed() {
    userId = UUID.randomUUID();
    AccountEntity account = new AccountEntity();
    account.setId(UUID.randomUUID());
    account.setUserId(userId);
    account.setAccountNumber("1234509879");
    account.setAccountType("PAYMENT");
    account.setCurrency("VND");
    account.setBalance(new BigDecimal("0.00"));
    account.setStatus("ACTIVE");
    accountRepository.save(account);
    accountId = account.getId();
  }

  @Test
  void cardRoundTripKeepsEncryptedPanAndStatus() {
    CardEntity card = card(CardStatus.PENDING_ACTIVATION);
    cardRepository.save(card);
    entityManager.flush();
    entityManager.clear();

    CardEntity loaded = cardRepository.findById(card.getId()).orElseThrow();
    assertEquals(CardStatus.PENDING_ACTIVATION, loaded.getStatus());
    assertEquals("1234", loaded.getPanLast4());
    assertTrue(loaded.getPanEncrypted().length() > 20);
    assertEquals(1, cardRepository.findByUserIdOrderByCreatedAtDesc(userId).size());
    assertTrue(
        cardRepository.existsByAccountIdAndStatusNotIn(
            accountId, java.util.List.of(CardStatus.CLOSED, CardStatus.REJECTED)));
  }

  @Test
  void partialUniqueIndexAllowsOnlyOneOpenCardPerAccount() {
    // CLOSED and REJECTED are terminal — neither blocks a live card
    cardRepository.save(card(CardStatus.CLOSED));
    cardRepository.save(card(CardStatus.REJECTED));
    cardRepository.save(card(CardStatus.ACTIVE));
    entityManager.flush();

    cardRepository.save(card(CardStatus.REQUESTED));
    // Raw em.flush() bypasses Spring's exception translation → Hibernate/JPA exception here.
    assertThrows(jakarta.persistence.PersistenceException.class, entityManager::flush);
  }

  @Test
  void requestedCardPersistsWithoutPan() {
    CardEntity requested = card(CardStatus.REQUESTED);
    requested.setPanEncrypted(null);
    requested.setPanLast4(null);
    requested.setExpiresOn(null);
    cardRepository.save(requested);
    entityManager.flush();
    entityManager.clear();

    CardEntity loaded = cardRepository.findById(requested.getId()).orElseThrow();
    assertEquals(CardStatus.REQUESTED, loaded.getStatus());
    assertEquals(null, loaded.getPanEncrypted());
  }

  private CardEntity card(CardStatus status) {
    CardEntity c = new CardEntity();
    c.setId(UUID.randomUUID());
    c.setAccountId(accountId);
    c.setUserId(userId);
    c.setPanEncrypted("ZW5jcnlwdGVkLXBhbi1kZW1vLXZhbHVlLWJhc2U2NA==");
    c.setPanLast4("1234");
    c.setStatus(status);
    c.setDailyLimit(new BigDecimal("20000000.00"));
    c.setExpiresOn(LocalDate.of(2029, 7, 27));
    return c;
  }
}
