package com.banksystem.transaction.application.transfer;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.application.transfer.impl.TransferSagaOrchestrator;
import com.banksystem.transaction.domain.transfer.TransferOrderEntity;
import com.banksystem.transaction.domain.transfer.TransferOrderRepository;
import com.banksystem.transaction.domain.transfer.TransferStatus;
import com.banksystem.transaction.infrastructure.napas.NapasSwitchClient;
import com.banksystem.transaction.infrastructure.napas.NapasSwitchClient.NapasPaymentResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Resolves asynchronous or timed-out NAPAS payments without risking a duplicate refund. */
@Service
public class NapasResolutionService {

  private static final Logger log = LoggerFactory.getLogger(NapasResolutionService.class);

  private final TransferOrderRepository repository;
  private final NapasSwitchClient napasSwitchClient;
  private final TransferSagaOrchestrator sagaOrchestrator;
  private final int maxAttempts;
  private final Duration inquiryAge;

  public NapasResolutionService(
      TransferOrderRepository repository,
      NapasSwitchClient napasSwitchClient,
      TransferSagaOrchestrator sagaOrchestrator,
      @Value("${bank.napas.status.max-attempts}") int maxAttempts,
      @Value("${bank.napas.status.min-age-seconds}") long inquiryAgeSeconds) {
    this.repository = repository;
    this.napasSwitchClient = napasSwitchClient;
    this.sagaOrchestrator = sagaOrchestrator;
    this.maxAttempts = Math.max(1, maxAttempts);
    this.inquiryAge = Duration.ofSeconds(Math.max(5, inquiryAgeSeconds));
  }

  @Scheduled(fixedDelayString = "${bank.napas.status.poll-ms}")
  public void resolveUnknownBatch() {
    Instant dueBefore = Instant.now().minus(inquiryAge);
    repository.findTop50ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            TransferStatus.UNKNOWN, dueBefore)
        .forEach(order -> resolve(order, "STATUS_INQUIRY"));
  }

  public TransferOrderEntity applyCallback(
      String clientRequestId, String providerReferenceId, NapasPaymentResponse response) {
    TransferOrderEntity order = findOrder(clientRequestId, providerReferenceId);
    return resolveKnownResponse(order, response, "CALLBACK");
  }

  public TransferOrderEntity inquireNow(UUID transferId) {
    TransferOrderEntity order = repository.findById(transferId)
        .orElseThrow(() -> new BusinessException("TRANSFER_NOT_FOUND", "Transfer not found"));
    return resolve(order, "MANUAL_INQUIRY");
  }

  /**
   * Resumes an order that stopped before the NAPAS request was persisted/sent. The source debit
   * and NAPAS payment both use the transfer id as their idempotency key, so retrying cannot create
   * a second money movement.
   */
  public TransferOrderEntity resumePending(UUID transferId) {
    TransferOrderEntity order = repository.findById(transferId)
        .orElseThrow(() -> new BusinessException("TRANSFER_NOT_FOUND", "Transfer not found"));
    if (order.getStatus() != TransferStatus.PENDING && order.getStatus() != TransferStatus.DEBITED) {
      throw new BusinessException(
          "TRANSFER_NOT_RESUMABLE", "Only PENDING or DEBITED transfers can be resumed");
    }
    return sagaOrchestrator.run(order);
  }

  private TransferOrderEntity resolve(TransferOrderEntity order, String source) {
    if (isTerminal(order)) {
      return order;
    }
    int attempt = order.getProviderAttemptCount() + 1;
    order.setProviderAttemptCount(attempt);
    order.setLastProviderQueryAt(Instant.now());
    order = repository.saveAndFlush(order);
    try {
      NapasPaymentResponse response = napasSwitchClient.inquirePayment(
          order.getId().toString(), order.getProviderReferenceId());
      return resolveKnownResponse(order, response, source);
    } catch (Exception ex) {
      String reason = "NAPAS_STATUS_INQUIRY_FAILED: " + safeMessage(ex);
      if (attempt >= maxAttempts) {
        return sagaOrchestrator.escalateExternalReview(order, reason);
      }
      order.setFailureReason(truncate(reason));
      order.setUpdatedAt(Instant.now());
      order = repository.saveAndFlush(order);
      log.warn("NAPAS status inquiry failed transfer={} attempt={}: {}",
          order.getId(), attempt, safeMessage(ex));
      return order;
    }
  }

  private TransferOrderEntity resolveKnownResponse(
      TransferOrderEntity order, NapasPaymentResponse response, String source) {
    if (isTerminal(order)) {
      return order;
    }
    return sagaOrchestrator.resolveExternalOutcome(order, response, source);
  }

  private TransferOrderEntity findOrder(String clientRequestId, String providerReferenceId) {
    if (clientRequestId != null && !clientRequestId.isBlank()) {
      try {
        return repository.findById(UUID.fromString(clientRequestId.trim()))
            .orElseThrow(() -> new BusinessException("TRANSFER_NOT_FOUND", "Transfer not found"));
      } catch (IllegalArgumentException ex) {
        throw new BusinessException("INVALID_CLIENT_REQUEST_ID", "Invalid client request id");
      }
    }
    if (providerReferenceId != null && !providerReferenceId.isBlank()) {
      return repository.findByProviderReferenceId(providerReferenceId.trim())
          .orElseThrow(() -> new BusinessException("TRANSFER_NOT_FOUND", "Transfer not found"));
    }
    throw new BusinessException("TRANSFER_REFERENCE_REQUIRED", "Transfer reference is required");
  }

  private boolean isTerminal(TransferOrderEntity order) {
    return order.getStatus() == TransferStatus.COMPLETED
        || order.getStatus() == TransferStatus.COMPENSATED
        || order.getStatus() == TransferStatus.FAILED;
  }

  private static String safeMessage(Exception ex) {
    return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
  }

  private static String truncate(String value) {
    return value.length() <= 250 ? value : value.substring(0, 250);
  }
}
