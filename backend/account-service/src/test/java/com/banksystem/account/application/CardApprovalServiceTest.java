package com.banksystem.account.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banksystem.account.api.dto.card.CardDtos.CardResponse;
import com.banksystem.account.application.card.CardApprovalService;
import com.banksystem.account.domain.entity.card.CardEntity;
import com.banksystem.account.domain.enums.card.CardStatus;
import com.banksystem.account.domain.repository.account.AccountRepository;
import com.banksystem.account.domain.repository.card.CardRepository;
import com.banksystem.account.infrastructure.feign.AuditClient;
import com.banksystem.account.infrastructure.feign.CustomerClient;
import com.banksystem.account.infrastructure.feign.NotificationClient;
import com.banksystem.account.infrastructure.feign.NotificationClientDtos.CreateNotificationRequest;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.common.security.CryptoUtils;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CardApprovalServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-27T03:00:00Z");
  private static final String AES_KEY = Base64.getEncoder().encodeToString(new byte[32]);

  private CardRepository cardRepository;
  private NotificationClient notificationClient;
  private AuditClient auditClient;
  private CardApprovalService service;

  private final UUID staffId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    cardRepository = mock(CardRepository.class);
    notificationClient = mock(NotificationClient.class);
    auditClient = mock(AuditClient.class);
    service =
        new CardApprovalService(
            cardRepository,
            mock(AccountRepository.class),
            mock(CustomerClient.class),
            auditClient,
            notificationClient,
            "customer-key",
            "tx-key",
            "notif-key",
            Clock.fixed(NOW, ZoneOffset.UTC),
            "Asia/Bangkok",
            AES_KEY,
            3);
  }

  @Test
  void approveGeneratesEncryptedLuhnPanAndNotifiesCustomer() {
    CardEntity card = requested();
    when(cardRepository.findById(card.getId())).thenReturn(Optional.of(card));

    CardResponse res = service.approve(card.getId(), staffId);

    String pan = CryptoUtils.decrypt(card.getPanEncrypted(), AES_KEY);
    assertTrue(CardNumberGenerator.isLuhnValid(pan));
    assertTrue(pan.startsWith("970459"));
    assertEquals(pan.substring(12), card.getPanLast4());
    assertEquals(CardStatus.PENDING_ACTIVATION, card.getStatus());
    assertEquals(staffId, card.getApprovedBy());
    assertEquals(2029, card.getExpiresOn().getYear());
    assertEquals("PENDING_ACTIVATION", res.status());

    ArgumentCaptor<CreateNotificationRequest> notif =
        ArgumentCaptor.forClass(CreateNotificationRequest.class);
    verify(notificationClient).createNotification(notif.capture(), eq("notif-key"));
    assertEquals("CARD_APPROVED", notif.getValue().template());
    assertEquals(card.getUserId(), notif.getValue().userId());
    verify(auditClient).createAuditLog(any(), eq("tx-key"));
  }

  @Test
  void rejectRequiresRequestedStateAndStoresReason() {
    CardEntity card = requested();
    when(cardRepository.findById(card.getId())).thenReturn(Optional.of(card));

    CardResponse res = service.reject(card.getId(), "KYC chua day du", staffId);

    assertEquals(CardStatus.REJECTED, card.getStatus());
    assertEquals("KYC chua day du", card.getRejectReason());
    assertEquals(staffId, card.getRejectedBy());
    assertEquals("KYC chua day du", res.rejectReason());
    // No PAN was ever created for a rejected request
    assertEquals(null, card.getPanEncrypted());

    // Deciding twice is invalid
    assertEquals(
        "CARD_NOT_REQUESTED",
        assertThrows(BusinessException.class, () -> service.approve(card.getId(), staffId))
            .getCode());
  }

  @Test
  void approveNonRequestedCardIsRejected() {
    CardEntity card = requested();
    card.setStatus(CardStatus.ACTIVE);
    when(cardRepository.findById(card.getId())).thenReturn(Optional.of(card));

    assertEquals(
        "CARD_NOT_REQUESTED",
        assertThrows(BusinessException.class, () -> service.approve(card.getId(), staffId))
            .getCode());
    verify(cardRepository, never()).save(any());
  }

  @Test
  void notificationOrAuditFailureNeverFailsTheDecision() {
    CardEntity card = requested();
    when(cardRepository.findById(card.getId())).thenReturn(Optional.of(card));
    when(notificationClient.createNotification(any(), anyString()))
        .thenThrow(new RuntimeException("notification down"));
    when(auditClient.createAuditLog(any(), anyString()))
        .thenThrow(new RuntimeException("transaction down"));

    CardResponse res = service.approve(card.getId(), staffId);

    assertEquals("PENDING_ACTIVATION", res.status());
  }

  private static CardEntity requested() {
    CardEntity c = new CardEntity();
    c.setId(UUID.randomUUID());
    c.setAccountId(UUID.randomUUID());
    c.setUserId(UUID.randomUUID());
    c.setStatus(CardStatus.REQUESTED);
    c.setDailyLimit(new BigDecimal("20000000"));
    c.setCreatedAt(NOW);
    return c;
  }
}
