package com.banksystem.transaction.application.sepay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.api.dto.SepayDtos.SepayWebhookPayload;
import com.banksystem.transaction.api.dto.SepayDtos.SepayWebhookResponse;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class SepayTransactionalInboxResilienceTest {

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
  private SepayService sepayService;
  private SepayReconciliationScheduler reconciliationScheduler;

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
    sepayService = new SepayService(orderRepository, webhookLogRepository, inboxService, executionService, accountGateway, verifier, properties);
    reconciliationScheduler = new SepayReconciliationScheduler(orderRepository, webhookLogRepository, accountGateway);
  }

  @Test
  @DisplayName("Fault Injection: Crash during PROCESSING after remote credit is self-healed by reconciliation")
  void faultInjection_crashAfterCredit_reconciledSuccessfully() {
    String orderCode = "SB777888";
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
        Instant.now().minusSeconds(180),
        Instant.now().plusSeconds(600));

    AccountView account = new AccountView(
        accountId.toString(), userId.toString(), accountNumber, "PAYMENT", "VND", BigDecimal.ZERO, "ACTIVE");
    when(accountGateway.getAccountByNumber(accountNumber)).thenReturn(account);

    SepayWebhookPayload payload = new SepayWebhookPayload(
        7777L, "MBBank", "2026-08-18 15:00:00", "0987654321", orderCode,
        "Chuyen tien " + orderCode, "in", BigDecimal.valueOf(100000),
        BigDecimal.valueOf(1000000), null, "MB7777", "Desc");

    // Phase 1: Webhook arrives. TX #1 commits durable log RECEIVED, then transitions to PROCESSING.
    // Simulate downstream credit completes, but crash occurs before TX #2 commits Order -> SUCCESS.
    SepayWebhookInboxService.InboxPersistResult result = inboxService.persistOrGet(payload, "{}");
    assertTrue(result.created());
    SepayWebhookLog committedInbox = result.log();
    assertNotNull(committedInbox);
    committedInbox.setProcessingStatus(SepayWebhookProcessingStatus.PROCESSING);
    assertEquals(7777L, committedInbox.getSepayTransactionId());

    // In memory state after crash: Order is still PENDING, but WebhookLog is PROCESSING in DB
    assertEquals(SepayOrderStatus.PENDING, order.getStatus());

    // Phase 2: System recovers and Reconciliation Scheduler runs
    when(orderRepository.findByStatusAndCreatedAtBefore(eq(SepayOrderStatus.PENDING), any()))
        .thenReturn(List.of(order));
    when(webhookLogRepository.findFirstByCodeOrderByCreatedAtDesc(orderCode))
        .thenReturn(Optional.of(committedInbox));

    reconciliationScheduler.reconcileStuckOrders();

    // Phase 3: Verify self-healing result: Order -> SUCCESS and WebhookLog -> PROCESSED
    assertEquals(SepayOrderStatus.SUCCESS, order.getStatus());
    assertEquals(7777L, order.getSepayTransactionId());
    assertEquals(SepayWebhookProcessingStatus.PROCESSED, committedInbox.getProcessingStatus());
    assertNotNull(order.getCompletedAt());

    ArgumentCaptor<MoneyCommand> commandCaptor = ArgumentCaptor.forClass(MoneyCommand.class);
    verify(accountGateway, atLeastOnce()).credit(eq(accountId), commandCaptor.capture());

    MoneyCommand executedCommand = commandCaptor.getValue();
    assertEquals("SEPAY-TX-7777-SB777888", executedCommand.referenceId());
    assertEquals("SEPAY-TX-7777-SB777888", executedCommand.commandId());
    assertEquals(BigDecimal.valueOf(100000), executedCommand.amount());
  }

  @Test
  @DisplayName("Fault Injection: FAILED_RETRYABLE state is self-healed by reconciliation")
  void faultInjection_failedRetryable_reconcilesToSuccess() {
    String orderCode = "SB777889";
    SepayPaymentOrder order = new SepayPaymentOrder(
        UUID.randomUUID(),
        orderCode,
        userId,
        accountNumber,
        BigDecimal.valueOf(50000),
        SepayOrderStatus.PENDING,
        "https://qr.sepay.vn/...",
        "MBBank",
        orderCode,
        Instant.now().minusSeconds(200),
        Instant.now().plusSeconds(600));

    AccountView account = new AccountView(
        accountId.toString(), userId.toString(), accountNumber, "PAYMENT", "VND", BigDecimal.ZERO, "ACTIVE");
    when(accountGateway.getAccountByNumber(accountNumber)).thenReturn(account);

    SepayWebhookLog failedInbox = new SepayWebhookLog(
        UUID.randomUUID(),
        8889L,
        "MBBank",
        "2026-08-18 15:00:00",
        accountNumber,
        orderCode,
        "Chuyen tien " + orderCode,
        "in",
        BigDecimal.valueOf(50000),
        BigDecimal.valueOf(1000000),
        "MB8889",
        SepayWebhookProcessingStatus.FAILED_RETRYABLE,
        "{}",
        "Timeout connecting to downstream",
        Instant.now().minusSeconds(180));

    when(orderRepository.findByStatusAndCreatedAtBefore(eq(SepayOrderStatus.PENDING), any()))
        .thenReturn(List.of(order));
    when(webhookLogRepository.findFirstByCodeOrderByCreatedAtDesc(orderCode))
        .thenReturn(Optional.of(failedInbox));

    reconciliationScheduler.reconcileStuckOrders();

    assertEquals(SepayOrderStatus.SUCCESS, order.getStatus());
    assertEquals(8889L, order.getSepayTransactionId());
    assertEquals(SepayWebhookProcessingStatus.PROCESSED, failedInbox.getProcessingStatus());
  }

  @Test
  @DisplayName("Duplicate Prevention: Duplicate webhook short-circuits immediately without regressing state or calling credit")
  void duplicateStateRegression_shortCircuitsImmediatelyWithoutCredit() {
    String orderCode = "SB777890";
    SepayWebhookLog existingProcessed = new SepayWebhookLog(
        UUID.randomUUID(),
        9999L,
        "MBBank",
        "2026-08-18 15:00:00",
        accountNumber,
        orderCode,
        "Chuyen tien " + orderCode,
        "in",
        BigDecimal.valueOf(200000),
        BigDecimal.valueOf(1000000),
        "MB9999",
        SepayWebhookProcessingStatus.PROCESSED,
        "{}",
        null,
        Instant.now().minusSeconds(100));

    when(webhookLogRepository.findBySepayTransactionId(9999L)).thenReturn(Optional.of(existingProcessed));

    SepayWebhookPayload payload = new SepayWebhookPayload(
        9999L, "MBBank", "2026-08-18 15:00:00", "0987654321", orderCode,
        "Chuyen tien " + orderCode, "in", BigDecimal.valueOf(200000),
        BigDecimal.valueOf(1000000), null, "MB9999", "Desc");

    SepayWebhookResponse response =
        sepayService.processWebhook("Apikey test-api-key", payload, "{}");

    assertTrue(response.success());
    assertEquals("Duplicate transaction skipped", response.message());
    assertEquals(SepayWebhookProcessingStatus.PROCESSED, existingProcessed.getProcessingStatus());
    verify(accountGateway, never()).credit(any(), any());
  }
}
