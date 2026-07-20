package com.banksystem.transaction.infrastructure.outbox;

import com.banksystem.transaction.domain.OutboxEventEntity;
import com.banksystem.transaction.domain.OutboxEventRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Polls PENDING outbox rows that are due, publishes to Kafka, then marks PUBLISHED.
 * Failures schedule exponential backoff; after max attempts the row becomes DEAD.
 */
@Component
public class OutboxPoller {

  private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

  private final OutboxEventRepository repository;
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final OutboxRetryPolicy retryPolicy;
  private final int batchSize;
  private final String topicCompleted;
  private final String topicFailed;

  public OutboxPoller(
      OutboxEventRepository repository,
      KafkaTemplate<String, String> kafkaTemplate,
      OutboxRetryPolicy retryPolicy,
      @Value("${bank.outbox.batch-size:50}") int batchSize,
      @Value("${bank.kafka.topic-completed}") String topicCompleted,
      @Value("${bank.kafka.topic-failed}") String topicFailed) {
    this.repository = repository;
    this.kafkaTemplate = kafkaTemplate;
    this.retryPolicy = retryPolicy;
    this.batchSize = batchSize;
    this.topicCompleted = topicCompleted;
    this.topicFailed = topicFailed;
  }

  @Scheduled(fixedDelayString = "${bank.outbox.poll-ms:1000}")
  @Transactional
  public void poll() {
    List<OutboxEventEntity> batch = repository.claimReady(retryPolicy.now(), batchSize);
    for (OutboxEventEntity event : batch) {
      publishOne(event);
    }
  }

  void publishOne(OutboxEventEntity event) {
    try {
      String topic = resolveTopic(event.getEventType());
      kafkaTemplate.send(topic, event.getAggregateId().toString(), event.getPayload()).get();
      event.markPublished(retryPolicy.now());
      repository.save(event);
      log.info(
          "Outbox published eventId={} type={} topic={} attempts={}",
          event.getId(),
          event.getEventType(),
          topic,
          event.getAttemptCount());
    } catch (Exception ex) {
      handleFailure(event, ex);
    }
  }

  private void handleFailure(OutboxEventEntity event, Exception ex) {
    int attempts = event.getAttemptCount() + 1;
    String message = rootMessage(ex);

    if (retryPolicy.isDead(attempts)) {
      event.markDead(attempts, message);
      repository.save(event);
      log.error(
          "Outbox DEAD eventId={} type={} attempts={}: {}",
          event.getId(),
          event.getEventType(),
          attempts,
          message);
      return;
    }

    event.markRetry(attempts, retryPolicy.nextAttemptAt(attempts), message);
    repository.save(event);
    log.warn(
        "Outbox publish failed eventId={} attempt={}/{} nextAttemptAt={}: {}",
        event.getId(),
        attempts,
        retryPolicy.maxAttempts(),
        event.getNextAttemptAt(),
        message);
  }

  private String resolveTopic(String eventType) {
    if (eventType != null && eventType.contains("FAILED")) {
      return topicFailed;
    }
    return topicCompleted;
  }

  /** Prefer root cause message (Kafka send().get() wraps ExecutionException). */
  static String rootMessage(Throwable ex) {
    Throwable cur = ex;
    while (cur.getCause() != null && cur.getCause() != cur) {
      cur = cur.getCause();
    }
    if (cur.getMessage() != null && !cur.getMessage().isBlank()) {
      return cur.getMessage();
    }
    return cur.getClass().getSimpleName();
  }
}
