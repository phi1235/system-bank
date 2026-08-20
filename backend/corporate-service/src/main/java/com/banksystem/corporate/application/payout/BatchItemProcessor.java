package com.banksystem.corporate.application.payout;

import com.banksystem.corporate.domain.payout.PayoutBatchEntity;
import com.banksystem.corporate.domain.payout.PayoutBatchRepository;
import com.banksystem.corporate.domain.payout.PayoutItemEntity;
import com.banksystem.corporate.domain.payout.PayoutItemRepository;
import com.banksystem.corporate.infrastructure.config.InternalApiKeyProperties;
import com.banksystem.corporate.infrastructure.feign.FeignClientDtos.CorporatePayoutTransferReq;
import com.banksystem.corporate.infrastructure.feign.FeignClientDtos.TransferResult;
import com.banksystem.corporate.infrastructure.feign.TransactionClient;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BatchItemProcessor {

  private static final Logger log = LoggerFactory.getLogger(BatchItemProcessor.class);

  private final PayoutBatchRepository batchRepository;
  private final PayoutItemRepository itemRepository;
  private final TransactionClient transactionClient;
  private final BatchItemStateService stateService;
  private final InternalApiKeyProperties apiKeyProperties;

  public BatchItemProcessor(
      PayoutBatchRepository batchRepository,
      PayoutItemRepository itemRepository,
      TransactionClient transactionClient,
      BatchItemStateService stateService,
      InternalApiKeyProperties apiKeyProperties) {
    this.batchRepository = batchRepository;
    this.itemRepository = itemRepository;
    this.transactionClient = transactionClient;
    this.stateService = stateService;
    this.apiKeyProperties = apiKeyProperties;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<UUID> claimChunk(UUID batchId, int chunkSize, String workerName, Instant now, Instant leaseUntil) {
    List<UUID> claimedIds = itemRepository.claimBatchItems(batchId, now, chunkSize);
    if (!claimedIds.isEmpty()) {
      itemRepository.markItemsClaimed(claimedIds, workerName, now, leaseUntil);
    }
    return claimedIds;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<UUID> claimReconciliationChunk(UUID batchId, int chunkSize, String workerName, Instant now, Instant leaseUntil) {
    List<UUID> claimedIds = itemRepository.claimReconciliationItems(batchId, now, chunkSize);
    if (!claimedIds.isEmpty()) {
      itemRepository.markItemsClaimed(claimedIds, workerName, now, leaseUntil);
    }
    return claimedIds;
  }

  public void processSingleItem(PayoutBatchEntity batch, UUID itemId) {
    PayoutItemEntity item = itemRepository.findById(itemId).orElse(null);
    if (item == null || !"CLAIMED".equals(item.getStatus())) {
      return;
    }

    String idempotencyKey = item.getIdempotencyKey() != null
        ? item.getIdempotencyKey()
        : "CORP:" + batch.getId() + ":" + item.getId() + ":v" + item.getExecutionVersion();

    String transferType = "SYSTEM_BANK".equalsIgnoreCase(item.getBankCode()) ? "INTERNAL" : "INTERBANK";

    CorporatePayoutTransferReq req = new CorporatePayoutTransferReq(
        batch.getCorporateId(),
        batch.getId(),
        item.getId(),
        batch.getHoldId(),
        batch.getSubmittedBy() != null ? batch.getSubmittedBy() : batch.getCreatedBy(),
        item.getExecutionVersion(),
        idempotencyKey,
        batch.getSourceAccountId(),
        item.getAccountNumber(),
        item.getAmount(),
        item.getDescription() != null ? item.getDescription() : "Chi tra luong",
        item.getCurrency(),
        transferType,
        item.getBankCode(),
        item.getBeneficiaryName()
    );

    // REMOTE HTTP CALL OUTSIDE OF DB TRANSACTION
    TransferResult result = null;
    Exception transferError = null;
    try {
      var resp = transactionClient.executeCorporatePayout(apiKeyProperties.getEffectiveTransactionApiKey(), req);
      if (resp != null && resp.data() != null) {
        result = resp.data();
      }
    } catch (Exception ex) {
      transferError = ex;
    }

    // Process result in its own short transaction
    if (transferError != null) {
      log.error("[ITEM-EXEC-ERROR] Network/Transient error executing item [{}] row {}: {}",
          item.getId(), item.getRowNumber(), transferError.getMessage());
      stateService.ambiguous(item.getId(), transferError.getMessage());
    } else if (result != null) {
      String status = result.status() != null ? result.status().toUpperCase() : "UNKNOWN";
      switch (status) {
        case "COMPLETED", "SUCCESS", "SETTLED" -> stateService.success(item.getId(), result.transactionId());
        case "FAILED", "REJECTED", "CANCELLED", "REFUNDED", "COMPENSATED" -> stateService.failed(item.getId(), result.failureReason());
        case "PROCESSING", "PENDING", "SUBMITTED", "UNKNOWN", "NETWORK_TIMEOUT" -> stateService.reconciling(item.getId(), status);
        default -> stateService.ambiguous(item.getId(), "Unrecognized transaction status: " + status);
      }
    } else {
      stateService.ambiguous(item.getId(), "Empty response received from transaction-service");
    }
  }

  public void reconcileSingleItem(PayoutBatchEntity batch, UUID itemId) {
    PayoutItemEntity item = itemRepository.findById(itemId).orElse(null);
    if (item == null || !"CLAIMED".equals(item.getStatus())) return;
    try {
      var response = transactionClient.inquireCorporatePayout(
          apiKeyProperties.getEffectiveTransactionApiKey(), batch.getCorporateId(), batch.getId(), item.getIdempotencyKey());
      TransferResult result = response != null ? response.data() : null;
      if (result == null) {
        stateService.ambiguous(itemId, "Empty transaction inquiry response");
        return;
      }
      String status = result.status() != null ? result.status().toUpperCase() : "UNKNOWN";
      switch (status) {
        case "COMPLETED", "SUCCESS", "SETTLED" -> stateService.success(itemId, result.transactionId());
        case "FAILED", "REJECTED", "CANCELLED", "REFUNDED", "COMPENSATED" -> stateService.failed(itemId, result.failureReason());
        case "PROCESSING", "PENDING", "SUBMITTED", "UNKNOWN", "NETWORK_TIMEOUT" -> stateService.reconciling(itemId, status);
        default -> stateService.ambiguous(itemId, "Unrecognized inquiry status: " + status);
      }
    } catch (Exception ex) {
      stateService.ambiguous(itemId, "Transaction inquiry failed: " + ex.getMessage());
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void updateBatchCounters(UUID batchId) {
    PayoutBatchEntity batch = batchRepository.findById(batchId).orElse(null);
    if (batch == null) return;

    long successfulCount = itemRepository.countByBatchIdAndStatus(batchId, "SUCCESS");
    long failedCount = itemRepository.countByBatchIdAndStatus(batchId, "FAILED_FINAL")
        + itemRepository.countByBatchIdAndStatus(batchId, "MANUAL_REVIEW");
    batch.setSuccessfulItems((int) successfulCount);
    batch.setFailedItems((int) failedCount);
    batch.setProcessedItems((int) (successfulCount + failedCount));
    batch.setUpdatedAt(Instant.now());
    batchRepository.saveAndFlush(batch);
  }
}
