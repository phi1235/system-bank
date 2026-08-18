package com.banksystem.transaction.infrastructure.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Application policy for outbox publish retries.
 * Exponential backoff with cap; dead-letter after max attempts.
 * Kept free of Kafka/JPA so unit tests stay pure.
 */
@Component
public class OutboxRetryPolicy {

  private final int maxAttempts;
  private final Duration baseDelay;
  private final Duration maxDelay;
  private final Clock clock;

  public OutboxRetryPolicy(
      @Value("${bank.outbox.max-attempts}") int maxAttempts,
      @Value("${bank.outbox.base-delay-ms}") long baseDelayMs,
      @Value("${bank.outbox.max-delay-ms}") long maxDelayMs,
      Clock clock) {
    if (maxAttempts < 1) {
      throw new IllegalArgumentException("bank.outbox.max-attempts must be >= 1");
    }
    this.maxAttempts = maxAttempts;
    this.baseDelay = Duration.ofMillis(Math.max(0, baseDelayMs));
    this.maxDelay = Duration.ofMillis(Math.max(baseDelayMs, maxDelayMs));
    this.clock = clock;
  }

  public int maxAttempts() {
    return maxAttempts;
  }

  public Instant now() {
    return clock.instant();
  }

  /**
   * @param attemptCountAfterFailure attempt counter after incrementing for this failure (1-based)
   */
  public boolean isDead(int attemptCountAfterFailure) {
    return attemptCountAfterFailure >= maxAttempts;
  }

  /**
   * Next retry time after a failure. attemptCountAfterFailure=1 → baseDelay, then doubles.
   */
  public Instant nextAttemptAt(int attemptCountAfterFailure) {
    long exp = Math.max(0, attemptCountAfterFailure - 1);
    // base * 2^(n-1), capped
    long factor = 1L << Math.min(exp, 20);
    long delayMs = Math.min(baseDelay.toMillis() * factor, maxDelay.toMillis());
    if (delayMs < 0) {
      delayMs = maxDelay.toMillis();
    }
    return clock.instant().plusMillis(delayMs);
  }

  public Duration baseDelay() {
    return baseDelay;
  }

  public Duration maxDelay() {
    return maxDelay;
  }
}
