package com.banksystem.corporate.application.payout;

import com.banksystem.corporate.application.audit.CorporateAuditService;
import com.banksystem.corporate.domain.payout.PayoutBatchEntity;
import com.banksystem.corporate.domain.payout.PayoutBatchRepository;
import com.banksystem.corporate.domain.payout.PayoutItemRepository;
import com.banksystem.corporate.infrastructure.config.InternalApiKeyProperties;
import com.banksystem.corporate.infrastructure.feign.AccountClient;
import com.banksystem.corporate.infrastructure.feign.FeignClientDtos.CreateBatchHoldReq;
import com.banksystem.corporate.infrastructure.feign.FeignClientDtos.HoldActionReq;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BatchExecutionWorker {

  private static final Logger log = LoggerFactory.getLogger(BatchExecutionWorker.class);

  private final PayoutBatchRepository batchRepository;
  private final PayoutItemRepository itemRepository;
  private final AccountClient accountClient;
  private final BatchItemProcessor itemProcessor;
  private final BatchLeaseService batchLeaseService;
  private final BatchCompletionService completionService;
  private final CorporateAuditService auditService;
  private final InternalApiKeyProperties apiKeyProperties;
  private final int chunkSize;
  private final int maxHoldRetries;
  private final long holdDurationSeconds;
  private final String workerId = "BATCH-WORKER-" + UUID.randomUUID();

  public BatchExecutionWorker(
      PayoutBatchRepository batchRepository,
      PayoutItemRepository itemRepository,
      AccountClient accountClient,
      BatchItemProcessor itemProcessor,
      BatchLeaseService batchLeaseService,
      BatchCompletionService completionService,
      CorporateAuditService auditService,
      InternalApiKeyProperties apiKeyProperties,
      @Value("${bank.payout.batch.chunk-size:50}") int chunkSize,
      @Value("${bank.payout.batch.hold-max-retries:10}") int maxHoldRetries,
      @Value("${bank.payout.batch.hold-duration-seconds:86400}") long holdDurationSeconds) {
    this.batchRepository = batchRepository;
    this.itemRepository = itemRepository;
    this.accountClient = accountClient;
    this.itemProcessor = itemProcessor;
    this.batchLeaseService = batchLeaseService;
    this.completionService = completionService;
    this.auditService = auditService;
    this.apiKeyProperties = apiKeyProperties;
    this.chunkSize = chunkSize;
    this.maxHoldRetries = maxHoldRetries;
    this.holdDurationSeconds = holdDurationSeconds;
  }

  @Scheduled(fixedDelayString = "${bank.payout.batch.worker-delay-ms:2000}")
  public void processBatches() {
    Instant now = Instant.now();
    List<UUID> reservableIds = batchLeaseService.claimForReservation(
        now, 20, workerId, now.plusSeconds(60));
    for (UUID batchId : reservableIds) {
      try {
        batchRepository.findById(batchId).ifPresent(this::reserveFundsAndStartProcessing);
      } catch (Exception e) {
        log.error("[WORKER-HOLD-ERROR] Failed to reserve funds for batch [{}]", batchId, e);
      }
    }

    Instant processingNow = Instant.now();
    List<UUID> processingIds = batchLeaseService.claimForProcessing(
        processingNow, 20, workerId, processingNow.plusSeconds(120));
    for (UUID batchId : processingIds) {
      try {
        batchRepository.findById(batchId).ifPresent(this::processBatchChunk);
      } catch (Exception e) {
        log.error("[WORKER-CHUNK-ERROR] Error processing items for batch [{}]", batchId, e);
      } finally {
        batchLeaseService.release(batchId);
      }
    }
  }

  private void reserveFundsAndStartProcessing(PayoutBatchEntity batch) {
    log.info("[WORKER-RESERVE] Reserving funds for batch [{}] Amount={} {}",
        batch.getId(), batch.getTotalAmount(), batch.getCurrency());

    try {
      String commandId = "HOLD-CORP-BATCH-" + batch.getId();
      var holdRes = accountClient.createBatchHold(
          apiKeyProperties.getEffectiveAccountApiKey(),
          batch.getSourceAccountId(),
          new CreateBatchHoldReq(batch.getId(), commandId, batch.getTotalAmount().add(batch.getTotalFee()), batch.getCurrency(), Instant.now().plusSeconds(holdDurationSeconds)));

      if (holdRes != null && holdRes.data() != null) {
        batchLeaseService.markReservationSucceeded(batch.getId(), holdRes.data().id());

        log.info("[WORKER-HOLD-SUCCESS] Reserved hold [{}] for batch [{}]. Items now QUEUED.", holdRes.data().id(), batch.getId());
      } else {
        markBatchReservationForRetry(batch, "Hold creation returned no result");
      }
    } catch (Exception e) {
      log.error("[WORKER-HOLD-FAILED] Exception during hold creation for batch [{}]: {}", batch.getId(), e.getMessage());
      // The request may have committed downstream while the response was lost. Re-submit the
      // deterministic command instead of declaring the batch failed and risking an orphan hold.
      markBatchReservationForRetry(batch, e.getMessage());
    }
  }

  private void processBatchChunk(PayoutBatchEntity batch) {
    Instant now = Instant.now();
    String workerName = workerId + "-ITEMS";
    Instant leaseUntil = now.plusSeconds(60);

    List<UUID> claimedIds = itemProcessor.claimChunk(batch.getId(), chunkSize, workerName, now, leaseUntil);
    List<UUID> reconciliationIds = itemProcessor.claimReconciliationChunk(
        batch.getId(), chunkSize, workerName + "-RECON", now, leaseUntil);
    if (claimedIds.isEmpty()) {
      if (reconciliationIds.isEmpty()) {
        batchRepository.findById(batch.getId()).ifPresent(this::checkBatchCompletion);
        return;
      }
    }

    for (UUID itemId : claimedIds) {
      itemProcessor.processSingleItem(batch, itemId);
    }
    for (UUID itemId : reconciliationIds) {
      itemProcessor.reconcileSingleItem(batch, itemId);
    }

    itemProcessor.updateBatchCounters(batch.getId());
    batchRepository.findById(batch.getId()).ifPresent(this::checkBatchCompletion);
  }

  private void checkBatchCompletion(PayoutBatchEntity batch) {
    long pendingCount = itemRepository.countByBatchIdAndStatus(batch.getId(), "QUEUED")
        + itemRepository.countByBatchIdAndStatus(batch.getId(), "CLAIMED")
        + itemRepository.countByBatchIdAndStatus(batch.getId(), "RETRY_WAIT")
        + itemRepository.countByBatchIdAndStatus(batch.getId(), "RECONCILING");

    if (pendingCount == 0) {
      if (batch.getHoldId() != null) {
        try {
          accountClient.releaseRemaining(apiKeyProperties.getEffectiveAccountApiKey(), batch.getHoldId(), new HoldActionReq("RELEASE-REMAINING-" + batch.getId(), null));
          log.info("[WORKER-RELEASE-HOLD] Released remaining funds hold [{}] for batch [{}]", batch.getHoldId(), batch.getId());
        } catch (Exception e) {
          log.warn("Failed to release remaining hold for batch [{}]; completion will retry: {}",
              batch.getId(), e.getMessage());
          return;
        }
      }

      PayoutBatchEntity completed = completionService.complete(batch.getId());

      log.info("[BATCH-COMPLETED] Payout Batch [{}] finished with status [{}] (Success: {}, Failed: {})",
          completed.getId(), completed.getStatus(), completed.getSuccessfulItems(), completed.getFailedItems());
    }
  }

  private void markBatchReservationForRetry(PayoutBatchEntity batch, String reason) {
    String status = batchLeaseService.markReservationFailed(batch.getId(), reason, maxHoldRetries);
    auditService.log(
        batch.getCorporateId(),
        batch.getCreatedBy(),
        "BATCH_HOLD_RETRY",
        "PAYOUT_BATCH",
        batch.getId().toString(),
        "status=" + status + ",reason=" + reason);
  }
}
