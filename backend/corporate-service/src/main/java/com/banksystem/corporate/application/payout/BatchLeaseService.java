package com.banksystem.corporate.application.payout;

import com.banksystem.corporate.domain.payout.PayoutBatchEntity;
import com.banksystem.corporate.domain.payout.PayoutBatchRepository;
import com.banksystem.corporate.domain.payout.PayoutItemRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BatchLeaseService {

  private final PayoutBatchRepository repository;
  private final PayoutItemRepository itemRepository;

  public BatchLeaseService(
      PayoutBatchRepository repository,
      PayoutItemRepository itemRepository) {
    this.repository = repository;
    this.itemRepository = itemRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<UUID> claimForReservation(
      Instant now, int limit, String worker, Instant leaseUntil) {
    List<UUID> ids = repository.claimReservableBatchIds(now, limit);
    if (!ids.isEmpty()) {
      repository.markBatchesClaimed(ids, "RESERVING_FUNDS", worker, leaseUntil, now);
    }
    return ids;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<UUID> claimForProcessing(
      Instant now, int limit, String worker, Instant leaseUntil) {
    List<UUID> ids = repository.claimProcessingBatchIds(now, limit);
    if (!ids.isEmpty()) {
      repository.markBatchesClaimed(ids, "PROCESSING", worker, leaseUntil, now);
    }
    return ids;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markReservationSucceeded(UUID batchId, UUID holdId) {
    PayoutBatchEntity batch = repository.findById(batchId).orElseThrow();
    batch.setHoldId(holdId);
    batch.setStatus("PROCESSING");
    batch.setStartedAt(batch.getStartedAt() == null ? Instant.now() : batch.getStartedAt());
    batch.setHoldNextRetryAt(null);
    batch.setHoldLastError(null);
    clearLease(batch);
    batch.setUpdatedAt(Instant.now());
    itemRepository.queueValidItems(batchId, Instant.now());
    repository.saveAndFlush(batch);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public String markReservationFailed(UUID batchId, String error, int maxRetries) {
    PayoutBatchEntity batch = repository.findById(batchId).orElseThrow();
    int retries = batch.getHoldRetryCount() + 1;
    batch.setHoldRetryCount(retries);
    batch.setHoldLastError(sanitize(error));
    if (retries >= maxRetries) {
      batch.setStatus("MANUAL_REVIEW");
      batch.setHoldNextRetryAt(null);
    } else {
      batch.setStatus("APPROVED");
      batch.setHoldNextRetryAt(Instant.now().plusSeconds(Math.min(900, retries * 30L)));
    }
    clearLease(batch);
    batch.setUpdatedAt(Instant.now());
    repository.saveAndFlush(batch);
    return batch.getStatus();
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void release(UUID batchId) {
    repository.findById(batchId).ifPresent(batch -> {
      clearLease(batch);
      batch.setUpdatedAt(Instant.now());
      repository.saveAndFlush(batch);
    });
  }

  private void clearLease(PayoutBatchEntity batch) {
    batch.setWorkerClaimedBy(null);
    batch.setWorkerLeaseUntil(null);
  }

  private String sanitize(String error) {
    String value = error == null || error.isBlank() ? "Unknown hold reservation error" : error;
    value = value.replace('\n', ' ').replace('\r', ' ');
    return value.length() <= 500 ? value : value.substring(0, 500);
  }
}
