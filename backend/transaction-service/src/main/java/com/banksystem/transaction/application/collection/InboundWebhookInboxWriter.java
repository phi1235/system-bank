package com.banksystem.transaction.application.collection;

import com.banksystem.transaction.domain.collection.InboundPaymentEventEntity;
import com.banksystem.transaction.domain.collection.InboundPaymentEventRepository;
import com.banksystem.transaction.domain.collection.InboundPaymentStatus;
import com.banksystem.transaction.infrastructure.va.VirtualAccountProvider.VerifiedInboundPayment;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InboundWebhookInboxWriter {

  private static final Logger log = LoggerFactory.getLogger(InboundWebhookInboxWriter.class);

  private final InboundPaymentEventRepository eventRepository;

  public InboundWebhookInboxWriter(InboundPaymentEventRepository eventRepository) {
    this.eventRepository = eventRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public InboundPaymentEventEntity insertReceived(VerifiedInboundPayment payment) {
    Instant now = Instant.now();
    InboundPaymentEventEntity entity = InboundPaymentEventEntity.create(
        payment.provider(),
        payment.providerTransactionId(),
        payment.virtualAccountNumber(),
        payment.bankBin(),
        payment.amount(),
        payment.currency(),
        payment.senderAccount(),
        payment.senderBankBin(),
        payment.senderName(),
        payment.referenceContent(),
        payment.rawPayloadHash(),
        payment.rawPayload(),
        now
    );
    return eventRepository.saveAndFlush(entity);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void updateStatusIndependent(UUID eventId, InboundPaymentStatus status, String errorMessage) {
    eventRepository.findById(eventId).ifPresent(event -> {
      event.setStatus(status);
      event.setErrorMessage(errorMessage);
      if (status == InboundPaymentStatus.PROCESSED) {
        event.setProcessedAt(Instant.now());
      }
      eventRepository.saveAndFlush(event);
      log.info("[INBOUND-INBOX] Updated event [{}] status -> {}", eventId, status);
    });
  }

  @Transactional(readOnly = true)
  public Optional<InboundPaymentEventEntity> findByProviderAndTxId(String provider, String providerTransactionId) {
    return eventRepository.findByProviderAndProviderTransactionId(provider, providerTransactionId);
  }
}
