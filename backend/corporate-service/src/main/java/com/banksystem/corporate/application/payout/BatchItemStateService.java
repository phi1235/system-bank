package com.banksystem.corporate.application.payout;

import com.banksystem.corporate.application.receipt.PayoutItemSucceededEvent;
import com.banksystem.corporate.domain.payout.PayoutItemEntity;
import com.banksystem.corporate.domain.payout.PayoutItemRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persists one payout-item outcome in an independent transaction. */
@Service
public class BatchItemStateService {
  private static final Logger log = LoggerFactory.getLogger(BatchItemStateService.class);

  private final PayoutItemRepository itemRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final int maxRetries;

  public BatchItemStateService(
      PayoutItemRepository itemRepository,
      ApplicationEventPublisher eventPublisher,
      @Value("${bank.payout.batch.max-retries:5}") int maxRetries) {
    this.itemRepository = itemRepository;
    this.eventPublisher = eventPublisher;
    this.maxRetries = maxRetries;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void success(UUID itemId, String txId) {
    PayoutItemEntity item = itemRepository.findById(itemId).orElse(null);
    if (item == null || "SUCCESS".equals(item.getStatus())) return;
    UUID transactionId;
    try {
      transactionId = UUID.fromString(txId);
    } catch (IllegalArgumentException | NullPointerException exception) {
      log.error("Completed payout item [{}] has an invalid transaction id: {}", itemId, txId);
      item.setStatus("MANUAL_REVIEW");
      item.setFailureReason("Completed transfer returned an invalid transaction id");
      item.setProcessedAt(Instant.now());
      clearLease(item);
      itemRepository.saveAndFlush(item);
      return;
    }

    item.setStatus("SUCCESS");
    item.setTransactionId(transactionId);
    item.setFailureReason(null);
    item.setProcessedAt(Instant.now());
    clearLease(item);
    itemRepository.saveAndFlush(item);
    eventPublisher.publishEvent(new PayoutItemSucceededEvent(itemId));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void failed(UUID itemId, String reason) {
    PayoutItemEntity item = itemRepository.findById(itemId).orElse(null);
    if (item == null) return;
    item.setStatus("FAILED_FINAL");
    item.setFailureReason(reason != null ? reason : "Transaction rejected");
    item.setProcessedAt(Instant.now());
    clearLease(item);
    itemRepository.saveAndFlush(item);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void reconciling(UUID itemId, String reason) {
    PayoutItemEntity item = itemRepository.findById(itemId).orElse(null);
    if (item == null) return;
    int attempts = item.getRetryCount() + 1;
    item.setRetryCount(attempts);
    if (attempts >= maxRetries) {
      item.setStatus("MANUAL_REVIEW");
      item.setFailureReason("Reconciliation attempts exhausted: " + reason);
      item.setProcessedAt(Instant.now());
    } else {
      item.setStatus("RECONCILING");
      item.setFailureReason("Pending inquiry: " + reason);
      item.setNextRetryAt(Instant.now().plusSeconds(Math.min(300, attempts * 30L)));
    }
    clearLease(item);
    itemRepository.saveAndFlush(item);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void ambiguous(UUID itemId, String error) {
    PayoutItemEntity item = itemRepository.findById(itemId).orElse(null);
    if (item == null) return;
    int attempts = item.getRetryCount() + 1;
    item.setRetryCount(attempts);
    if (attempts < maxRetries) {
      item.setStatus("RETRY_WAIT");
      item.setNextRetryAt(Instant.now().plusSeconds(attempts * 15L));
      item.setFailureReason(error);
    } else {
      item.setStatus("MANUAL_REVIEW");
      item.setFailureReason("Max retries reached: " + error);
      item.setProcessedAt(Instant.now());
    }
    clearLease(item);
    itemRepository.saveAndFlush(item);
  }

  private void clearLease(PayoutItemEntity item) {
    item.setLeaseUntil(null);
    item.setClaimedBy(null);
    item.setUpdatedAt(Instant.now());
  }
}
