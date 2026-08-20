package com.banksystem.corporate.application.outbox;

import com.banksystem.corporate.domain.outbox.CorporateOutboxEventEntity;
import com.banksystem.corporate.domain.outbox.CorporateOutboxEventRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CorporateOutboxWorker {

  private static final Logger log = LoggerFactory.getLogger(CorporateOutboxWorker.class);
  private static final String TOPIC = "corporate-events";
  private static final int MAX_RETRIES = 5;

  private final CorporateOutboxEventRepository outboxRepository;
  private final CorporateOutboxStateService stateService;
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final String workerId = "OUTBOX-WORKER-" + UUID.randomUUID();

  public CorporateOutboxWorker(
      CorporateOutboxEventRepository outboxRepository,
      CorporateOutboxStateService stateService,
      KafkaTemplate<String, String> kafkaTemplate) {
    this.outboxRepository = outboxRepository;
    this.stateService = stateService;
    this.kafkaTemplate = kafkaTemplate;
  }

  @Scheduled(fixedDelay = 1000)
  public void processOutbox() {
    Instant now = Instant.now();
    var claimedIds = stateService.claim(now, 50, workerId, now.plusSeconds(30));
    if (claimedIds.isEmpty()) {
      return;
    }

    var events = outboxRepository.findAllById(claimedIds);
    for (CorporateOutboxEventEntity event : events) {
      try {
        kafkaTemplate.send(TOPIC, event.getAggregateId().toString(), event.getPayload()).get();
        stateService.processed(event.getId());
      } catch (Exception e) {
        log.error("[OUTBOX-DISPATCH-ERROR] Failed to send outbox event [{}]: {}", event.getId(), e.getMessage());
        stateService.failed(event.getId(), e.getMessage(), MAX_RETRIES);
      }
    }
  }
}
