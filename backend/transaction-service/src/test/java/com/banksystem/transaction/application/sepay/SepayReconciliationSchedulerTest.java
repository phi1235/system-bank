package com.banksystem.transaction.application.sepay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banksystem.transaction.application.gateway.AccountGateway;
import com.banksystem.transaction.domain.sepay.SepayOrderStatus;
import com.banksystem.transaction.domain.sepay.SepayPaymentOrder;
import com.banksystem.transaction.domain.sepay.SepayPaymentOrderRepository;
import com.banksystem.transaction.domain.sepay.SepayWebhookLog;
import com.banksystem.transaction.domain.sepay.SepayWebhookLogRepository;
import com.banksystem.transaction.domain.sepay.SepayWebhookProcessingStatus;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.AccountView;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.MoneyCommand;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SepayReconciliationSchedulerTest {

  @Mock
  private SepayPaymentOrderRepository orderRepository;

  @Mock
  private SepayWebhookLogRepository webhookLogRepository;

  @Mock
  private AccountGateway accountGateway;

  private SepayReconciliationScheduler scheduler;

  private final UUID userId = UUID.randomUUID();
  private final UUID accountId = UUID.randomUUID();
  private final String accountNumber = "1000123456";

  @BeforeEach
  void setUp() {
    scheduler = new SepayReconciliationScheduler(orderRepository, webhookLogRepository, accountGateway);
  }

  @Test
  void reconcileStuckOrders_recoversStuckOrder() {
    String orderCode = "SB987654";
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
        Instant.now().minusSeconds(300),
        Instant.now().plusSeconds(600));

    when(orderRepository.findByStatusAndCreatedAtBefore(any(), any()))
        .thenReturn(List.of(order));

    SepayWebhookLog webhookLog = new SepayWebhookLog(
        UUID.randomUUID(),
        8888L,
        "MBBank",
        "2026-08-18 15:00:00",
        accountNumber,
        orderCode,
        "Chuyen khoan " + orderCode,
        "in",
        BigDecimal.valueOf(100000),
        BigDecimal.valueOf(500000),
        "REF8888",
        SepayWebhookProcessingStatus.PROCESSED,
        "{}",
        null,
        Instant.now().minusSeconds(200));

    when(webhookLogRepository.findFirstByCodeOrderByCreatedAtDesc(orderCode))
        .thenReturn(Optional.of(webhookLog));

    AccountView account = new AccountView(
        accountId.toString(), userId.toString(), accountNumber, "PAYMENT", "VND", BigDecimal.ZERO, "ACTIVE");
    when(accountGateway.getAccountByNumber(accountNumber)).thenReturn(account);

    scheduler.reconcileStuckOrders();

    assertEquals(SepayOrderStatus.SUCCESS, order.getStatus());
    assertEquals(8888L, order.getSepayTransactionId());
    verify(accountGateway).credit(any(UUID.class), any(MoneyCommand.class));
    verify(orderRepository).save(order);
  }

  @Test
  void reconcileStuckOrders_marksExpiredOrder() {
    String orderCode = "SB987655";
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
        Instant.now().minusSeconds(1000),
        Instant.now().minusSeconds(100)); // Already expired

    when(orderRepository.findByStatusAndCreatedAtBefore(any(), any()))
        .thenReturn(List.of(order));
    when(webhookLogRepository.findFirstByCodeOrderByCreatedAtDesc(orderCode))
        .thenReturn(Optional.empty());

    scheduler.reconcileStuckOrders();

    assertEquals(SepayOrderStatus.EXPIRED, order.getStatus());
    verify(orderRepository).save(order);
  }
}
