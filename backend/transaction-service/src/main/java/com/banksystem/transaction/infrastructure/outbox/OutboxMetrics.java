package com.banksystem.transaction.infrastructure.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Outbox operational counters for Prometheus/actuator.
 * Names follow Micrometer convention (suffix _total added by registry for counters).
 */
@Component
public class OutboxMetrics {

  private final Counter published;
  private final Counter retry;
  private final Counter dead;
  private final Counter replayed;

  public OutboxMetrics(MeterRegistry registry) {
    this.published = Counter.builder("bank.outbox.published")
        .description("Outbox events successfully published to Kafka")
        .register(registry);
    this.retry = Counter.builder("bank.outbox.retry")
        .description("Outbox publish failures that scheduled a retry")
        .register(registry);
    this.dead = Counter.builder("bank.outbox.dead")
        .description("Outbox events moved to DEAD after max attempts")
        .register(registry);
    this.replayed = Counter.builder("bank.outbox.replayed")
        .description("DEAD outbox events re-queued by ops replay")
        .register(registry);
  }

  public void incrementPublished() {
    published.increment();
  }

  public void incrementRetry() {
    retry.increment();
  }

  public void incrementDead() {
    dead.increment();
  }

  public void incrementReplayed() {
    replayed.increment();
  }
}
