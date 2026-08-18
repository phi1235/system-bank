package com.banksystem.transaction.application.sepay;

import com.banksystem.transaction.application.gateway.AccountGateway;
import com.banksystem.transaction.domain.sepay.SepayOrderStatus;
import com.banksystem.transaction.domain.sepay.SepayPaymentOrder;
import com.banksystem.transaction.domain.sepay.SepayPaymentOrderRepository;
import com.banksystem.transaction.domain.sepay.SepayWebhookLog;
import com.banksystem.transaction.domain.sepay.SepayWebhookLogRepository;
import com.banksystem.transaction.domain.sepay.SepayWebhookProcessingStatus;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.AccountView;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.MoneyCommand;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SepayReconciliationScheduler {

  private static final Logger log = LoggerFactory.getLogger(SepayReconciliationScheduler.class);

  private final SepayPaymentOrderRepository orderRepository;
  private final SepayWebhookLogRepository webhookLogRepository;
  private final AccountGateway accountGateway;

  public SepayReconciliationScheduler(
      SepayPaymentOrderRepository orderRepository,
      SepayWebhookLogRepository webhookLogRepository,
      AccountGateway accountGateway) {
    this.orderRepository = orderRepository;
    this.webhookLogRepository = webhookLogRepository;
    this.accountGateway = accountGateway;
  }

  @Scheduled(fixedDelay = 60000)
  @Transactional
  public void reconcileStuckOrders() {
    Instant threshold = Instant.now().minus(2, ChronoUnit.MINUTES);
    List<SepayPaymentOrder> pendingOrders = orderRepository.findByStatusAndCreatedAtBefore(
        SepayOrderStatus.PENDING, threshold);

    for (SepayPaymentOrder order : pendingOrders) {
      Optional<SepayWebhookLog> webhookLogOpt = webhookLogRepository.findFirstByCodeOrderByCreatedAtDesc(order.getOrderCode());
      if (webhookLogOpt.isPresent()) {
        SepayWebhookLog webhookLog = webhookLogOpt.get();
        if ((webhookLog.getProcessingStatus() == SepayWebhookProcessingStatus.PROCESSED
            || webhookLog.getProcessingStatus() == SepayWebhookProcessingStatus.RECEIVED
            || webhookLog.getProcessingStatus() == SepayWebhookProcessingStatus.PROCESSING
            || webhookLog.getProcessingStatus() == SepayWebhookProcessingStatus.IN_PROGRESS
            || webhookLog.getProcessingStatus() == SepayWebhookProcessingStatus.FAILED_RETRYABLE)
            && webhookLog.getTransferAmount() != null
            && webhookLog.getTransferAmount().compareTo(order.getAmount()) == 0) {
          try {
            AccountView account = accountGateway.getAccountByNumber(order.getAccountNumber());
            if (account != null) {
              String idempotencyKey = "SEPAY-TX-" + webhookLog.getSepayTransactionId() + "-" + order.getOrderCode();
              MoneyCommand creditCommand = new MoneyCommand(
                  order.getAmount(),
                  idempotencyKey,
                  "Nạp tiền VietQR SePay (Recon) " + order.getOrderCode(),
                  idempotencyKey);

              accountGateway.credit(account.idUuid(), creditCommand);

              order.setStatus(SepayOrderStatus.SUCCESS);
              order.setSepayTransactionId(webhookLog.getSepayTransactionId());
              order.setCompletedAt(Instant.now());
              orderRepository.save(order);

              webhookLog.setProcessingStatus(SepayWebhookProcessingStatus.PROCESSED);
              webhookLog.setErrorMessage(null);
              webhookLogRepository.save(webhookLog);
              log.info("Reconciliation self-healed stuck SePay order: {}", order.getOrderCode());
            }
          } catch (Exception ex) {
            webhookLog.setProcessingStatus(SepayWebhookProcessingStatus.FAILED_RETRYABLE);
            webhookLog.setErrorMessage("Reconciliation credit failed: " + ex.getMessage());
            webhookLogRepository.save(webhookLog);
            log.error("Failed to reconcile order {}: {}", order.getOrderCode(), ex.getMessage());
          }
        }
      } else if (order.getExpiresAt().isBefore(Instant.now())) {
        order.setStatus(SepayOrderStatus.EXPIRED);
        orderRepository.save(order);
        log.info("Marked expired SePay order: {}", order.getOrderCode());
      }
    }
  }
}
