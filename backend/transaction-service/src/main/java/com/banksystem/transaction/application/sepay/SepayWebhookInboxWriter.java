package com.banksystem.transaction.application.sepay;

import com.banksystem.transaction.api.dto.SepayDtos.SepayWebhookPayload;
import com.banksystem.transaction.domain.sepay.SepayWebhookLog;
import com.banksystem.transaction.domain.sepay.SepayWebhookLogRepository;
import com.banksystem.transaction.domain.sepay.SepayWebhookProcessingStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SepayWebhookInboxWriter {

  private static final Logger log = LoggerFactory.getLogger(SepayWebhookInboxWriter.class);

  private final SepayWebhookLogRepository webhookLogRepository;

  public SepayWebhookInboxWriter(
      SepayWebhookLogRepository webhookLogRepository) {
    this.webhookLogRepository = webhookLogRepository;
  }

  /**
   * TX #1: Executes INSERT in an isolated physical transaction.
   * If UNIQUE(sepay_transaction_id) fails, this transaction rolls back cleanly.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public SepayWebhookLog insertReceived(SepayWebhookPayload payload, String rawPayload) {
    SepayWebhookLog logEntity = new SepayWebhookLog(
        UUID.randomUUID(),
        payload.id(),
        payload.gateway(),
        payload.transactionDate(),
        payload.accountNumber(),
        payload.code(),
        payload.content(),
        payload.transferType(),
        payload.transferAmount(),
        payload.accumulated(),
        payload.referenceCode(),
        SepayWebhookProcessingStatus.RECEIVED,
        rawPayload,
        null,
        Instant.now());

    SepayWebhookLog saved = webhookLogRepository.saveAndFlush(logEntity);
    log.info("Durably committed SePay webhook inbox record in isolated TX: id={}, txId={}, status=RECEIVED",
        saved.getId(), payload.id());
    return saved;
  }

  /**
   * Independent status transition (e.g., RECEIVED -> PROCESSING).
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void updateStatusIndependent(UUID logId, SepayWebhookProcessingStatus status, String errorMessage) {
    Optional<SepayWebhookLog> logOpt = webhookLogRepository.findById(logId);
    if (logOpt.isPresent()) {
      SepayWebhookLog logEntity = logOpt.get();
      logEntity.setProcessingStatus(status);
      logEntity.setErrorMessage(errorMessage);
      webhookLogRepository.saveAndFlush(logEntity);
      log.debug("Updated SePay webhook log {} to status {} in independent TX", logId, status);
    }
  }

  /**
   * Queries existing record in a fresh, unpolluted read-only transaction.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
  public Optional<SepayWebhookLog> findBySepayTransactionId(Long sepayTxId) {
    return webhookLogRepository.findBySepayTransactionId(sepayTxId);
  }
}
