package com.banksystem.transaction.application.sepay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.api.dto.SepayDtos.CreateTopUpRequest;
import com.banksystem.transaction.api.dto.SepayDtos.SepayWebhookPayload;
import com.banksystem.transaction.api.dto.SepayDtos.SepayWebhookResponse;
import com.banksystem.transaction.api.dto.SepayDtos.TopUpOrderResponse;
import com.banksystem.transaction.application.gateway.AccountGateway;
import com.banksystem.transaction.config.SepayProperties;
import com.banksystem.transaction.domain.sepay.SepayOrderStatus;
import com.banksystem.transaction.domain.sepay.SepayPaymentOrder;
import com.banksystem.transaction.domain.sepay.SepayPaymentOrderRepository;
import com.banksystem.transaction.domain.sepay.SepayWebhookLog;
import com.banksystem.transaction.domain.sepay.SepayWebhookLogRepository;
import com.banksystem.transaction.domain.sepay.SepayWebhookProcessingStatus;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.AccountView;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.MoneyCommand;
import com.banksystem.transaction.infrastructure.sepay.SepayWebhookVerifier;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SepayServiceTest {

  @Mock
  private SepayPaymentOrderRepository orderRepository;

  @Mock
  private SepayWebhookLogRepository webhookLogRepository;

  @Mock
  private AccountGateway accountGateway;

  @Mock
  private SepayWebhookVerifier verifier;

  private SepayWebhookInboxService inboxService;
  private SepayProperties properties;
  private SepayService service;

  private final UUID userId = UUID.randomUUID();
  private final UUID accountId = UUID.randomUUID();
  private final String accountNumber = "1000123456";

  @BeforeEach
  void setUp() {
    properties = new SepayProperties();
    properties.setApiKey("test-api-key");
    properties.setAccountNumber("0987654321");
    properties.setBankName("MBBank");
    properties.setAccountName("NGUYEN CHAU PHI");
    properties.setQrTemplate("compact2");
    properties.setOrderExpiryMinutes(15);

    lenient().when(webhookLogRepository.saveAndFlush(any()))
        .thenAnswer(invocation -> {
          SepayWebhookLog entity = invocation.getArgument(0);
          lenient().when(webhookLogRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
          return entity;
        });

    SepayWebhookInboxWriter writer = new SepayWebhookInboxWriter(webhookLogRepository);
    inboxService = new SepayWebhookInboxService(writer);
    SepayOrderExecutionService executionService = new SepayOrderExecutionService(orderRepository, webhookLogRepository);
    service = new SepayService(orderRepository, webhookLogRepository, inboxService, executionService, accountGateway, verifier, properties);
  }

  @Test
  void createTopUpOrder_success() {
    AccountView account = new AccountView(
        accountId.toString(), userId.toString(), accountNumber, "PAYMENT", "VND", BigDecimal.ZERO, "ACTIVE");
    when(accountGateway.getAccountByNumber(accountNumber)).thenReturn(account);

    CreateTopUpRequest request = new CreateTopUpRequest(accountNumber, BigDecimal.valueOf(500000), "Top-up test");
    TopUpOrderResponse response = service.createTopUpOrder(userId, request);

    assertNotNull(response);
    assertNotNull(response.orderCode());
    assertTrue(response.orderCode().startsWith("SB"));
    assertEquals(accountNumber, response.accountNumber());
    assertEquals(BigDecimal.valueOf(500000), response.amount());
    assertEquals("PENDING", response.status());
    assertTrue(response.vietQrUrl().contains("MBBank"));
    assertTrue(response.vietQrUrl().contains("0987654321"));

    ArgumentCaptor<SepayPaymentOrder> orderCaptor = ArgumentCaptor.forClass(SepayPaymentOrder.class);
    verify(orderRepository).save(orderCaptor.capture());
    assertEquals(SepayOrderStatus.PENDING, orderCaptor.getValue().getStatus());
  }

  @Test
  void createTopUpOrder_accountNotFound_throwsException() {
    when(accountGateway.getAccountByNumber(accountNumber)).thenReturn(null);

    CreateTopUpRequest request = new CreateTopUpRequest(accountNumber, BigDecimal.valueOf(500000), "Top-up test");
    BusinessException ex = assertThrows(BusinessException.class, () -> service.createTopUpOrder(userId, request));
    assertEquals("ACCOUNT_NOT_FOUND", ex.getCode());
  }

  @Test
  void createTopUpOrder_forbidden_throwsException() {
    UUID otherUser = UUID.randomUUID();
    AccountView account = new AccountView(
        accountId.toString(), otherUser.toString(), accountNumber, "PAYMENT", "VND", BigDecimal.ZERO, "ACTIVE");
    when(accountGateway.getAccountByNumber(accountNumber)).thenReturn(account);

    CreateTopUpRequest request = new CreateTopUpRequest(accountNumber, BigDecimal.valueOf(500000), "Top-up test");
    BusinessException ex = assertThrows(BusinessException.class, () -> service.createTopUpOrder(userId, request));
    assertEquals("FORBIDDEN", ex.getCode());
  }

  @Test
  void processWebhook_success() {
    String orderCode = "SB1234567";
    SepayPaymentOrder order = new SepayPaymentOrder(
        UUID.randomUUID(),
        orderCode,
        userId,
        accountNumber,
        BigDecimal.valueOf(200000),
        SepayOrderStatus.PENDING,
        "https://qr.sepay.vn/...",
        "MBBank",
        orderCode,
        Instant.now(),
        Instant.now().plusSeconds(900));

    when(webhookLogRepository.findBySepayTransactionId(9999L)).thenReturn(Optional.empty());
    when(orderRepository.findByOrderCode(orderCode)).thenReturn(Optional.of(order));

    AccountView account = new AccountView(
        accountId.toString(), userId.toString(), accountNumber, "PAYMENT", "VND", BigDecimal.ZERO, "ACTIVE");
    when(accountGateway.getAccountByNumber(accountNumber)).thenReturn(account);

    SepayWebhookPayload payload = new SepayWebhookPayload(
        9999L, "MBBank", "2026-08-18 15:00:00", "0987654321", orderCode,
        "Chuyen tien " + orderCode, "in", BigDecimal.valueOf(200000),
        BigDecimal.valueOf(1000000), null, "MB9999", "Desc");

    SepayWebhookResponse response = service.processWebhook("Apikey test-api-key", payload, "{}");

    assertTrue(response.success());
    assertEquals(SepayOrderStatus.SUCCESS, order.getStatus());
    assertEquals(9999L, order.getSepayTransactionId());
    assertNotNull(order.getCompletedAt());

    verify(accountGateway).credit(any(UUID.class), any(MoneyCommand.class));
    verify(webhookLogRepository, atLeastOnce()).saveAndFlush(any(SepayWebhookLog.class));
  }

  @Test
  void processWebhook_duplicateWebhook_ignored() {
    SepayWebhookLog existing = new SepayWebhookLog(
        UUID.randomUUID(), 9999L, "MBBank", "2026-08-18 15:00:00", "0987654321", "SB123",
        "Content", "in", BigDecimal.valueOf(200000), BigDecimal.valueOf(1000000), "REF9999",
        SepayWebhookProcessingStatus.PROCESSED, "{}", null, Instant.now());
    when(webhookLogRepository.findBySepayTransactionId(9999L)).thenReturn(Optional.of(existing));

    SepayWebhookPayload payload = new SepayWebhookPayload(
        9999L, "MBBank", "2026-08-18 15:00:00", "0987654321", "SB123",
        "Content", "in", BigDecimal.valueOf(200000),
        BigDecimal.valueOf(1000000), null, "MB9999", "Desc");

    SepayWebhookResponse response = service.processWebhook("Apikey test-api-key", payload, "{}");

    assertTrue(response.success());
    verify(accountGateway, never()).credit(any(), any());
  }

  @Test
  void processWebhook_underpaid_marksManualReview() {
    String orderCode = "SB1234567";
    SepayPaymentOrder order = new SepayPaymentOrder(
        UUID.randomUUID(),
        orderCode,
        userId,
        accountNumber,
        BigDecimal.valueOf(500000),
        SepayOrderStatus.PENDING,
        "https://qr.sepay.vn/...",
        "MBBank",
        orderCode,
        Instant.now(),
        Instant.now().plusSeconds(900));

    when(webhookLogRepository.findBySepayTransactionId(9999L)).thenReturn(Optional.empty());
    when(orderRepository.findByOrderCode(orderCode)).thenReturn(Optional.of(order));

    // Sent only 200,000 instead of 500,000
    SepayWebhookPayload payload = new SepayWebhookPayload(
        9999L, "MBBank", "2026-08-18 15:00:00", "0987654321", orderCode,
        "Chuyen tien " + orderCode, "in", BigDecimal.valueOf(200000),
        BigDecimal.valueOf(1000000), null, "MB9999", "Desc");

    SepayWebhookResponse response = service.processWebhook("Apikey test-api-key", payload, "{}");

    assertTrue(response.success());
    assertEquals(SepayOrderStatus.MANUAL_REVIEW, order.getStatus());
    verify(accountGateway, never()).credit(any(), any());
  }

  @Test
  void processWebhook_overpaid_marksManualReview() {
    String orderCode = "SB1234567";
    SepayPaymentOrder order = new SepayPaymentOrder(
        UUID.randomUUID(),
        orderCode,
        userId,
        accountNumber,
        BigDecimal.valueOf(100000),
        SepayOrderStatus.PENDING,
        "https://qr.sepay.vn/...",
        "MBBank",
        orderCode,
        Instant.now(),
        Instant.now().plusSeconds(900));

    when(webhookLogRepository.findBySepayTransactionId(9999L)).thenReturn(Optional.empty());
    when(orderRepository.findByOrderCode(orderCode)).thenReturn(Optional.of(order));

    // Sent 150,000 instead of 100,000 (Overpaid)
    SepayWebhookPayload payload = new SepayWebhookPayload(
        9999L, "MBBank", "2026-08-18 15:00:00", "0987654321", orderCode,
        "Chuyen tien " + orderCode, "in", BigDecimal.valueOf(150000),
        BigDecimal.valueOf(1000000), null, "MB9999", "Desc");

    SepayWebhookResponse response = service.processWebhook("Apikey test-api-key", payload, "{}");

    assertTrue(response.success());
    assertEquals(SepayOrderStatus.MANUAL_REVIEW, order.getStatus());
    verify(accountGateway, never()).credit(any(), any());
  }
}
