package com.banksystem.transaction.application.transfer;

import com.banksystem.transaction.application.transfer.impl.TransferSagaOrchestrator;
import com.banksystem.transaction.domain.transfer.TransferOrderEntity;
import com.banksystem.transaction.domain.transfer.TransferOrderRepository;
import com.banksystem.transaction.domain.transfer.TransferStatus;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "bank.recon.enabled", havingValue = "true", matchIfMissing = true)
public class TransferReconciliationScheduler {

  private static final Logger log = LoggerFactory.getLogger(TransferReconciliationScheduler.class);

  private final TransferOrderRepository transferOrderRepository;
  private final TransferSagaOrchestrator sagaOrchestrator;
  private final IdempotencyClaimService idempotencyClaimService;
  private final TransferFeeGlService feeGlService;

  public TransferReconciliationScheduler(
      TransferOrderRepository transferOrderRepository,
      TransferSagaOrchestrator sagaOrchestrator,
      IdempotencyClaimService idempotencyClaimService,
      TransferFeeGlService feeGlService) {
    this.transferOrderRepository = transferOrderRepository;
    this.sagaOrchestrator = sagaOrchestrator;
    this.idempotencyClaimService = idempotencyClaimService;
    this.feeGlService = feeGlService;
  }

  @Scheduled(fixedDelayString = "${bank.recon.poll-ms:15000}")
  public void pollPendingReconciliations() {
    Instant now = Instant.now();
    List<TransferOrderEntity> pendingOrders = transferOrderRepository
        .findReconciliationEligible(now, List.of(TransferStatus.UNKNOWN, TransferStatus.REVIEW_REQUIRED));

    if (pendingOrders.isEmpty()) {
      return;
    }

    log.info("Found {} pending transfer orders eligible for reconciliation", pendingOrders.size());
    for (TransferOrderEntity order : pendingOrders) {
      try {
        sagaOrchestrator.reconcileOrder(order);
      } catch (Exception ex) {
        log.error("Error reconciling transfer order {}: {}", order.getId(), ex.getMessage());
      }
    }
  }

  @Scheduled(fixedDelayString = "${bank.recon.fee-poll-ms:60000}")
  public void reconcilePendingFees() {
    List<TransferOrderEntity> pendingFeeOrders = transferOrderRepository.findPendingFeeGlOrders();
    if (pendingFeeOrders.isEmpty()) {
      return;
    }

    log.info("Found {} completed transfer orders with pending Fee GL reconciliation", pendingFeeOrders.size());
    for (TransferOrderEntity order : pendingFeeOrders) {
      try {
        String feeLedgerId = feeGlService.postFee(order);
        if (feeLedgerId != null) {
          order.setFeeEntryRef(feeLedgerId);
          order.setUpdatedAt(Instant.now());
          transferOrderRepository.save(order);
          log.info("Reconciled pending Fee GL for transfer {}: ledger={}", order.getId(), feeLedgerId);
        }
      } catch (Exception ex) {
        log.warn("Fee GL reconciliation attempt failed for transfer {}: {}", order.getId(), ex.getMessage());
      }
    }
  }

  @Scheduled(cron = "0 0 2 * * *")
  public void cleanupExpiredIdempotencyClaims() {
    int deleted = idempotencyClaimService.cleanupExpiredClaims();
    if (deleted > 0) {
      log.info("Cleaned up {} expired idempotency claims from database", deleted);
    }
  }
}
