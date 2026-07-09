package com.banksystem.transaction.infrastructure.outbox;

import com.banksystem.transaction.domain.OutboxEventEntity;
import com.banksystem.transaction.domain.OutboxEventRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxPoller {

  private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

  private final OutboxEventRepository repository;
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final int batchSize;
  private final String topicCompleted;
  private final String topicFailed;

  public OutboxPoller(
      OutboxEventRepository repository,
      KafkaTemplate<String, String> kafkaTemplate,
      @Value("${bank.outbox.batch-size:50}") int batchSize,
      @Value("${bank.kafka.topic-completed}") String topicCompleted,
      @Value("${bank.kafka.topic-failed}") String topicFailed) {
    this.repository = repository;
    this.kafkaTemplate = kafkaTemplate;
    this.batchSize = batchSize;
    this.topicCompleted = topicCompleted;
    this.topicFailed = topicFailed;
  }

  @Scheduled(fixedDelayString = "${bank.outbox.poll-ms:1000}")
  @Transactional
  public void poll() {
    List<OutboxEventEntity> batch = repository.findUnpublished(batchSize);
    for (OutboxEventEntity event : batch) {
      try {
        String topic = resolveTopic(event.getEventType());
        kafkaTemplate.send(topic, event.getAggregateId().toString(), event.getPayload()).get();
        event.setPublishedAt(Instant.now());
        repository.save(event);
        log.info("Outbox published eventId={} type={} topic={}", event.getId(), event.getEventType(), topic);
      } catch (Exception ex) {
        log.warn("Outbox publish failed eventId={}: {}", event.getId(), ex.getMessage());
      }
    }
  }

  private String resolveTopic(String eventType) {
    if (eventType != null && eventType.contains("FAILED")) {
      return topicFailed;
    }
    return topicCompleted;
  }
}
