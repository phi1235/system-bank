package com.banksystem.transaction.application.openbanking;

import com.banksystem.transaction.domain.outbox.OutboxEventEntity;
import com.banksystem.transaction.domain.outbox.OutboxEventRepository;
import com.banksystem.transaction.domain.outbox.OutboxStatus;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OpenBankingWebhookDispatcher {

  private static final Logger log = LoggerFactory.getLogger(OpenBankingWebhookDispatcher.class);

  private final OutboxEventRepository outboxEventRepository;

  public OpenBankingWebhookDispatcher(OutboxEventRepository outboxEventRepository) {
    this.outboxEventRepository = outboxEventRepository;
  }

  @Scheduled(fixedDelayString = "${bank.openbanking.webhook.poll-delay-ms:5000}")
  @Transactional
  public void dispatchPendingWebhooks() {
    Instant now = Instant.now();
    List<OutboxEventEntity> pendingEvents = outboxEventRepository.claimReady(now, 50);

    for (OutboxEventEntity event : pendingEvents) {
      if (!"ISO_PAYMENT".equalsIgnoreCase(event.getAggregateType())) {
        continue;
      }
      try {
        log.info("Dispatching B2B ISO 20022 webhook callback: id={}, eventType={}, dedupeKey={}",
            event.getId(), event.getEventType(), event.getDedupeKey());

        // In real network: execute HTTP POST to Client Webhook Callback URL with HMAC signature
        event.setStatus(OutboxStatus.PUBLISHED.name());
        event.setPublishedAt(Instant.now());
        outboxEventRepository.save(event);
      } catch (Exception ex) {
        log.error("Failed to dispatch B2B webhook event {}: {}", event.getId(), ex.getMessage());
        event.setAttemptCount(event.getAttemptCount() + 1);
        event.setLastError(ex.getMessage());
        event.setNextAttemptAt(Instant.now().plusSeconds(Math.min(60 * event.getAttemptCount(), 3600)));
        if (event.getAttemptCount() >= 5) {
          event.setStatus(OutboxStatus.DEAD.name());
        }
        outboxEventRepository.save(event);
      }
    }
  }
}
