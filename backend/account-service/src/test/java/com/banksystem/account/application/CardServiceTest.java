package com.banksystem.account.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banksystem.account.api.dto.CardDtos.CardResponse;
import com.banksystem.account.api.dto.CardDtos.CardRevealResponse;
import com.banksystem.account.api.dto.CardDtos.UpdateCardLimitRequest;
import com.banksystem.account.domain.AccountEntity;
import com.banksystem.account.domain.CardEntity;
import com.banksystem.account.domain.CardRepository;
import com.banksystem.account.domain.CardStatus;
import com.banksystem.account.domain.AccountRepository;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.common.security.CryptoUtils;
import com.banksystem.common.security.GatewayUser;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.banksystem.account.application.mapper.CardMapper;

class CardServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-27T03:00:00Z");
  private static final String AES_KEY = Base64.getEncoder().encodeToString(new byte[32]);

  private CardRepository cardRepository;
  private AccountAccessService access;
  private AccountRepository accountRepository;
  private CardService service;

  private final UUID accountId = UUID.randomUUID();
  private final UUID ownerId = UUID.randomUUID();
  private final GatewayUser owner = new GatewayUser(ownerId, List.of(), List.of("ib:cards:view"));

  @BeforeEach
  void setUp() {
    cardRepository = mock(CardRepository.class);
    access = mock(AccountAccessService.class);
    accountRepository = mock(AccountRepository.class);
    service =
        new CardService(
            cardRepository,
            access,
            accountRepository,
            new CardMapper(),
            Clock.fixed(NOW, ZoneOffset.UTC),
            "Asia/Bangkok",
            AES_KEY,
            new BigDecimal("20000000"),
            new BigDecimal("100000000"));
    when(accountRepository.findById(accountId)).thenReturn(Optional.of(account("ACTIVE")));
  }

  @Test
  void requestEntersApprovalQueueWithoutAnyPan() {
    when(access.requireOwnedOrStaff(accountId, owner)).thenReturn(account("ACTIVE"));
    when(cardRepository.existsByAccountIdAndStatusNotIn(
            eq(accountId), eq(java.util.List.of(CardStatus.CLOSED, CardStatus.REJECTED))))
        .thenReturn(false);

    CardResponse res = service.request(accountId, owner);

    ArgumentCaptor<CardEntity> saved = ArgumentCaptor.forClass(CardEntity.class);
    verify(cardRepository).save(saved.capture());
    CardEntity card = saved.getValue();
    assertEquals(CardStatus.REQUESTED, card.getStatus());
    // The PAN must NOT exist before approval
    assertEquals(null, card.getPanEncrypted());
    assertEquals(null, card.getPanLast4());
    assertEquals(null, card.getExpiresOn());
    assertEquals(0, card.getDailyLimit().compareTo(new BigDecimal("20000000")));
    assertEquals(null, res.maskedPan());
    assertEquals("REQUESTED", res.status());
  }

  @Test
  void requestRejectsFrozenAccountAndDuplicateRequest() {
    when(access.requireOwnedOrStaff(accountId, owner)).thenReturn(account("FROZEN"));
    assertEquals(
        "ACCOUNT_NOT_ACTIVE",
        assertThrows(BusinessException.class, () -> service.request(accountId, owner)).getCode());

    when(access.requireOwnedOrStaff(accountId, owner)).thenReturn(account("ACTIVE"));
    when(cardRepository.existsByAccountIdAndStatusNotIn(
            eq(accountId), eq(java.util.List.of(CardStatus.CLOSED, CardStatus.REJECTED))))
        .thenReturn(true);
    assertEquals(
        "CARD_ALREADY_EXISTS",
        assertThrows(BusinessException.class, () -> service.request(accountId, owner)).getCode());
    verify(cardRepository, never()).save(any());
  }

  @Test
  void lifecycleTransitionsAreValidated() {
    CardEntity card = card(CardStatus.PENDING_ACTIVATION);
    when(cardRepository.findById(card.getId())).thenReturn(Optional.of(card));
    when(access.requireOwnedOrStaff(accountId, owner)).thenReturn(account("ACTIVE"));

    assertEquals("ACTIVE", service.activate(card.getId(), owner).status());

    // ACTIVE -> activate again is invalid
    BusinessException ex =
        assertThrows(BusinessException.class, () -> service.activate(card.getId(), owner));
    assertEquals("CARD_INVALID_TRANSITION", ex.getCode());

    assertEquals("LOCKED", service.lock(card.getId(), owner).status());
    assertEquals("ACTIVE", service.unlock(card.getId(), owner).status());
    assertEquals("CLOSED", service.close(card.getId(), owner).status());
    assertEquals(
        "CARD_INVALID_TRANSITION",
        assertThrows(BusinessException.class, () -> service.close(card.getId(), owner)).getCode());
  }

  @Test
  void revealReturnsFullPanOnlyWhenActive() {
    String pan = CardNumberGenerator.generate();
    CardEntity card = card(CardStatus.ACTIVE);
    card.setPanEncrypted(CryptoUtils.encrypt(pan, AES_KEY));
    when(cardRepository.findById(card.getId())).thenReturn(Optional.of(card));
    when(access.requireOwnedOrStaff(accountId, owner)).thenReturn(account("ACTIVE"));

    CardRevealResponse res = service.reveal(card.getId(), owner);
    assertEquals(pan, res.pan());

    card.setStatus(CardStatus.LOCKED);
    assertEquals(
        "CARD_NOT_ACTIVE",
        assertThrows(BusinessException.class, () -> service.reveal(card.getId(), owner))
            .getCode());
  }

  @Test
  void updateLimitEnforcesMax() {
    CardEntity card = card(CardStatus.ACTIVE);
    when(cardRepository.findById(card.getId())).thenReturn(Optional.of(card));
    when(access.requireOwnedOrStaff(accountId, owner)).thenReturn(account("ACTIVE"));

    assertEquals(
        "CARD_LIMIT_TOO_HIGH",
        assertThrows(
                BusinessException.class,
                () ->
                    service.updateLimit(
                        card.getId(),
                        new UpdateCardLimitRequest(new BigDecimal("200000000")),
                        owner))
            .getCode());

    CardResponse res =
        service.updateLimit(card.getId(), new UpdateCardLimitRequest(new BigDecimal("5000000")), owner);
    assertEquals(0, res.dailyLimit().compareTo(new BigDecimal("5000000")));
  }

  private AccountEntity account(String status) {
    AccountEntity a = new AccountEntity();
    a.setId(accountId);
    a.setUserId(ownerId);
    a.setAccountNumber("1057199609");
    a.setStatus(status);
    return a;
  }

  private CardEntity card(CardStatus status) {
    CardEntity c = new CardEntity();
    c.setId(UUID.randomUUID());
    c.setAccountId(accountId);
    c.setUserId(ownerId);
    c.setPanEncrypted(CryptoUtils.encrypt(CardNumberGenerator.generate(), AES_KEY));
    c.setPanLast4("1234");
    c.setStatus(status);
    c.setDailyLimit(new BigDecimal("20000000"));
    c.setExpiresOn(java.time.LocalDate.of(2029, 7, 27));
    return c;
  }
}
