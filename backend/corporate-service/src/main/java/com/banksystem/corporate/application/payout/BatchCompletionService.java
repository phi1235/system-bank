package com.banksystem.corporate.application.payout;

import com.banksystem.corporate.application.audit.CorporateAuditService;
import com.banksystem.corporate.domain.outbox.CorporateOutboxEventEntity;
import com.banksystem.corporate.domain.outbox.CorporateOutboxEventRepository;
import com.banksystem.corporate.domain.payout.PayoutBatchEntity;
import com.banksystem.corporate.domain.payout.PayoutBatchRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BatchCompletionService {

  private final PayoutBatchRepository batchRepository;
  private final CorporateOutboxEventRepository outboxRepository;
  private final CorporateAuditService auditService;

  public BatchCompletionService(
      PayoutBatchRepository batchRepository,
      CorporateOutboxEventRepository outboxRepository,
      CorporateAuditService auditService) {
    this.batchRepository = batchRepository;
    this.outboxRepository = outboxRepository;
    this.auditService = auditService;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public PayoutBatchEntity complete(UUID batchId) {
    PayoutBatchEntity batch = batchRepository.findById(batchId).orElseThrow();
    int success = batch.getSuccessfulItems();
    int failed = batch.getFailedItems();
    if (failed == 0 && success > 0) {
      batch.setStatus("COMPLETED");
    } else if (success > 0) {
      batch.setStatus("PARTIALLY_COMPLETED");
    } else {
      batch.setStatus("FAILED");
    }
    batch.setCompletedAt(Instant.now());
    batch.setUpdatedAt(Instant.now());
    batch.setWorkerClaimedBy(null);
    batch.setWorkerLeaseUntil(null);
    batchRepository.saveAndFlush(batch);

    outboxRepository.save(CorporateOutboxEventEntity.of(
        "PAYOUT_BATCH",
        batch.getId(),
        "PAYOUT_BATCH_COMPLETED",
        "{\"batchId\":\"" + batch.getId() + "\",\"status\":\"" + batch.getStatus()
            + "\",\"success\":" + success + ",\"failed\":" + failed + "}"));
    auditService.log(
        batch.getCorporateId(),
        batch.getSubmittedBy() != null ? batch.getSubmittedBy() : batch.getCreatedBy(),
        "BATCH_EXECUTION_COMPLETED",
        "PAYOUT_BATCH",
        batch.getId().toString(),
        "Status=" + batch.getStatus() + ",Success=" + success + ",Failed=" + failed);
    return batch;
  }
}
