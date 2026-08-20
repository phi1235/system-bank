package com.banksystem.corporate.application.receipt;

import com.banksystem.corporate.domain.payout.PayoutBatchEntity;
import com.banksystem.corporate.domain.payout.PayoutBatchRepository;
import com.banksystem.corporate.domain.payout.PayoutItemEntity;
import com.banksystem.corporate.domain.payout.PayoutItemRepository;
import com.banksystem.corporate.domain.receipt.ReceiptArtifactEntity;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ReceiptRecoveryWorker {

  private static final Logger log = LoggerFactory.getLogger(ReceiptRecoveryWorker.class);
  private static final int RECOVERY_BATCH_SIZE = 100;

  private final PayoutItemRepository itemRepository;
  private final PayoutBatchRepository batchRepository;
  private final ReceiptService receiptService;
  private final ReceiptArtifactLinkService linkService;

  public ReceiptRecoveryWorker(
      PayoutItemRepository itemRepository,
      PayoutBatchRepository batchRepository,
      ReceiptService receiptService,
      ReceiptArtifactLinkService linkService) {
    this.itemRepository = itemRepository;
    this.batchRepository = batchRepository;
    this.receiptService = receiptService;
    this.linkService = linkService;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void afterPayoutSucceeded(PayoutItemSucceededEvent event) {
    ensureItemReceipt(event.itemId());
  }

  @Scheduled(fixedDelayString = "${bank.payout.receipt-recovery-delay-ms:30000}")
  public void recoverMissingArtifacts() {
    itemRepository.findSuccessfulItemIdsMissingReceipt(RECOVERY_BATCH_SIZE)
        .forEach(this::ensureItemReceipt);
    batchRepository.findCompletedBatchIdsMissingReport(RECOVERY_BATCH_SIZE)
        .forEach(this::ensureBatchReport);
  }

  private void ensureItemReceipt(UUID itemId) {
    try {
      PayoutItemEntity item = itemRepository.findById(itemId).orElse(null);
      if (item == null || !"SUCCESS".equals(item.getStatus()) || item.getReceiptArtifactId() != null) {
        return;
      }
      PayoutBatchEntity batch = batchRepository.findById(item.getBatchId()).orElse(null);
      if (batch == null) {
        return;
      }
      ReceiptArtifactEntity artifact = receiptService.generateItemReceipt(batch, item);
      linkService.link(itemId, artifact.getId());
    } catch (RuntimeException exception) {
      log.warn("[RECEIPT-RECOVERY] Item receipt generation deferred for item [{}]: {}",
          itemId, exception.getMessage());
    }
  }

  private void ensureBatchReport(UUID batchId) {
    try {
      PayoutBatchEntity batch = batchRepository.findById(batchId).orElse(null);
      if (batch == null) {
        return;
      }
      List<PayoutItemEntity> items = itemRepository.findByBatchIdOrderByRowNumberAsc(batchId);
      receiptService.generateBatchConsolidatedReport(batch, items);
    } catch (RuntimeException exception) {
      log.warn("[RECEIPT-RECOVERY] Consolidated report generation deferred for batch [{}]: {}",
          batchId, exception.getMessage());
    }
  }
}
