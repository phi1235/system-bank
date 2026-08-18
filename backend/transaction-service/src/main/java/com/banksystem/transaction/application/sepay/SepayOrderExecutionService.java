package com.banksystem.transaction.application.sepay;

import com.banksystem.transaction.domain.sepay.SepayOrderStatus;
import com.banksystem.transaction.domain.sepay.SepayPaymentOrder;
import com.banksystem.transaction.domain.sepay.SepayPaymentOrderRepository;
import com.banksystem.transaction.domain.sepay.SepayWebhookLog;
import com.banksystem.transaction.domain.sepay.SepayWebhookLogRepository;
import com.banksystem.transaction.domain.sepay.SepayWebhookProcessingStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SepayOrderExecutionService {

  private static final Logger log = LoggerFactory.getLogger(SepayOrderExecutionService.class);

  private final SepayPaymentOrderRepository orderRepository;
  private final SepayWebhookLogRepository webhookLogRepository;

  public SepayOrderExecutionService(
      SepayPaymentOrderRepository orderRepository,
      SepayWebhookLogRepository webhookLogRepository) {
    this.orderRepository = orderRepository;
    this.webhookLogRepository = webhookLogRepository;
  }

  /**
   * TX #2: Executed via independent Spring Bean proxy.
   * Atomically commits BOTH Order -> SUCCESS and WebhookLog -> PROCESSED in the SAME database transaction.
   * If any failure or crash occurs, BOTH roll back together.
   */
  @Transactional
  public void completeOrderAndLog(SepayPaymentOrder order, Long sepayTxId, UUID webhookLogId) {
    order.setStatus(SepayOrderStatus.SUCCESS);
    order.setSepayTransactionId(sepayTxId);
    order.setCompletedAt(Instant.now());
    orderRepository.save(order);

    SepayWebhookLog logEntity = webhookLogRepository.findById(webhookLogId)
        .orElseThrow(() -> new IllegalStateException("SePay webhook log not found: " + webhookLogId));
    logEntity.setProcessingStatus(SepayWebhookProcessingStatus.PROCESSED);
    logEntity.setErrorMessage(null);
    webhookLogRepository.save(logEntity);

    log.info("Atomically committed order completion in TX #2: code={}, status=SUCCESS, logId={}",
        order.getOrderCode(), webhookLogId);
  }

  /**
   * TX #2 (Manual Review): Executed via independent Spring Bean proxy.
   * Atomically commits BOTH Order -> MANUAL_REVIEW and WebhookLog -> MANUAL_REVIEW in the SAME database transaction.
   */
  @Transactional
  public void markManualReview(SepayPaymentOrder order, Long sepayTxId, UUID webhookLogId, BigDecimal receivedAmount) {
    order.setStatus(SepayOrderStatus.MANUAL_REVIEW);
    order.setSepayTransactionId(sepayTxId);
    orderRepository.save(order);

    String errorMsg = "Amount mismatch: expected " + order.getAmount() + " but received " + receivedAmount;
    SepayWebhookLog logEntity = webhookLogRepository.findById(webhookLogId)
        .orElseThrow(() -> new IllegalStateException("SePay webhook log not found: " + webhookLogId));
    logEntity.setProcessingStatus(SepayWebhookProcessingStatus.MANUAL_REVIEW);
    logEntity.setErrorMessage(errorMsg);
    webhookLogRepository.save(logEntity);

    log.warn("Atomically committed order manual review in TX #2: code={}, reason={}", order.getOrderCode(), errorMsg);
  }
}
