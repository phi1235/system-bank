package com.banksystem.transaction.infrastructure.outbox;

import com.banksystem.transaction.application.reconciliation.OpsAlertPublisher;
import com.banksystem.transaction.domain.outbox.OutboxEventEntity;
import com.banksystem.transaction.domain.outbox.OutboxEventRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Polls PENDING outbox rows that are due, publishes to Kafka, then marks PUBLISHED.
 * Failures schedule exponential backoff; after max attempts the row becomes DEAD.
 *
 * <p>Claiming and status updates run in short transactions; the (blocking) Kafka send
 * happens outside any transaction so DB row locks are never held across broker I/O.
 * Claimed rows carry a lease ({@code next_attempt_at} pushed forward) so other poller
 * instances skip them while this one publishes; if the process dies mid-batch the
 * lease expires and the rows become claimable again (at-least-once, consumer dedupes).
 */
@Component
public class OutboxPoller {

  private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

  private final OutboxEventRepository repository;
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final OutboxRetryPolicy retryPolicy;
  private final OutboxMetrics metrics;
  private final OpsAlertPublisher opsAlertPublisher;
  private final TransactionTemplate transactionTemplate;
  private final int batchSize;
  private final long claimLeaseSeconds;
  private final String topicCompleted;
  private final String topicFailed;

  public OutboxPoller(
      OutboxEventRepository repository,
      KafkaTemplate<String, String> kafkaTemplate,
      OutboxRetryPolicy retryPolicy,
      OutboxMetrics metrics,
      OpsAlertPublisher opsAlertPublisher,
      TransactionTemplate transactionTemplate,
      @Value("${bank.outbox.batch-size}") int batchSize,
      @Value("${bank.outbox.claim-lease-seconds}") long claimLeaseSeconds,
      @Value("${bank.kafka.topic-completed}") String topicCompleted,
      @Value("${bank.kafka.topic-failed}") String topicFailed) {
    this.repository = repository;
    this.kafkaTemplate = kafkaTemplate;
    this.retryPolicy = retryPolicy;
    this.metrics = metrics;
    this.opsAlertPublisher = opsAlertPublisher;
    this.transactionTemplate = transactionTemplate;
    this.batchSize = batchSize;
    this.claimLeaseSeconds = claimLeaseSeconds;
    this.topicCompleted = topicCompleted;
    this.topicFailed = topicFailed;
  }

  @Scheduled(fixedDelayString = "${bank.outbox.poll-ms}")
  public void poll() {
    List<OutboxEventEntity> batch = claimBatch();
    for (OutboxEventEntity event : batch) {
      publishOne(event);
    }
  }

  /** Short transaction: claim due rows and lease them so other instances skip them. */
  private List<OutboxEventEntity> claimBatch() {
    return transactionTemplate.execute(tx -> {
      List<OutboxEventEntity> batch = repository.claimReady(retryPolicy.now(), batchSize);
      if (!batch.isEmpty()) {
        Instant leaseUntil = retryPolicy.now().plusSeconds(claimLeaseSeconds);
        batch.forEach(event -> event.setNextAttemptAt(leaseUntil));
        repository.saveAll(batch);
      }
      return batch;
    });
  }

  void publishOne(OutboxEventEntity event) {
    try {
      String topic = resolveTopic(event.getEventType());
      // Blocking broker I/O happens outside any DB transaction.
      kafkaTemplate.send(topic, event.getAggregateId().toString(), event.getPayload()).get();
      event.markPublished(retryPolicy.now());
      saveInNewTx(event);
      metrics.incrementPublished();
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
      saveInNewTx(event);
      metrics.incrementDead();
      log.error(
          "Outbox DEAD eventId={} type={} attempts={}: {}",
          event.getId(),
          event.getEventType(),
          attempts,
          message);
      opsAlertPublisher.outboxDead(event);
      return;
    }

    event.markRetry(attempts, retryPolicy.nextAttemptAt(attempts), message);
    saveInNewTx(event);
    metrics.incrementRetry();
    log.warn(
        "Outbox publish failed eventId={} attempt={}/{} nextAttemptAt={}: {}",
        event.getId(),
        attempts,
        retryPolicy.maxAttempts(),
        event.getNextAttemptAt(),
        message);
  }

  private void saveInNewTx(OutboxEventEntity event) {
    transactionTemplate.executeWithoutResult(tx -> repository.save(event));
  }

  private String resolveTopic(String eventType) {
    if (eventType != null && (eventType.contains("FAILED")
        || eventType.contains("REVIEW") || eventType.contains("UNKNOWN"))) {
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
