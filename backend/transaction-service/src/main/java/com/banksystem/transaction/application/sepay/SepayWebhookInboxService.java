package com.banksystem.transaction.application.sepay;

import com.banksystem.transaction.api.dto.SepayDtos.SepayWebhookPayload;
import com.banksystem.transaction.domain.sepay.SepayWebhookLog;
import com.banksystem.transaction.domain.sepay.SepayWebhookProcessingStatus;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class SepayWebhookInboxService {

  private static final Logger log = LoggerFactory.getLogger(SepayWebhookInboxService.class);

  public record InboxPersistResult(
      SepayWebhookLog log,
      boolean created
  ) {}

  private final SepayWebhookInboxWriter inboxWriter;

  public SepayWebhookInboxService(SepayWebhookInboxWriter inboxWriter) {
    this.inboxWriter = inboxWriter;
  }

  /**
   * Durably inserts webhook in an isolated transaction.
   * If a UNIQUE constraint conflict occurs, the failed insert transaction rolls back completely first,
   * then we query the existing row in a fresh, clean transaction (preventing PostgreSQL aborted transaction errors).
   */
  public InboxPersistResult persistOrGet(SepayWebhookPayload payload, String rawPayload) {
    Long sepayTxId = payload.id();
    if (sepayTxId != null) {
      Optional<SepayWebhookLog> existing = inboxWriter.findBySepayTransactionId(sepayTxId);
      if (existing.isPresent()) {
        log.info("Known duplicate SePay webhook transaction ID: {}", sepayTxId);
        return new InboxPersistResult(existing.get(), false);
      }
    }

    try {
      SepayWebhookLog created = inboxWriter.insertReceived(payload, rawPayload);
      return new InboxPersistResult(created, true);
    } catch (DataIntegrityViolationException ex) {
      log.warn("Unique constraint hit on sepay_transaction_id {}. Rollback complete. Retrieving existing record via fresh TX.", sepayTxId);
      SepayWebhookLog existing = inboxWriter.findBySepayTransactionId(sepayTxId)
          .orElseThrow(() -> ex);
      return new InboxPersistResult(existing, false);
    }
  }

  public void updateStatusIndependent(UUID logId, SepayWebhookProcessingStatus status, String errorMessage) {
    inboxWriter.updateStatusIndependent(logId, status, errorMessage);
  }

  public Optional<SepayWebhookLog> findBySepayTransactionId(Long sepayTxId) {
    return inboxWriter.findBySepayTransactionId(sepayTxId);
  }
}
