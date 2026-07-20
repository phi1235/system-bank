package com.banksystem.transaction.infrastructure.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OutboxRetryPolicyTest {

  private static final Instant NOW = Instant.parse("2026-07-21T01:00:00Z");
  private OutboxRetryPolicy policy;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    // maxAttempts=3, base=1s, max=8s
    policy = new OutboxRetryPolicy(3, 1000, 8000, clock);
  }

  @Test
  void firstFailureSchedulesBaseDelay() {
    Instant next = policy.nextAttemptAt(1);
    assertEquals(NOW.plus(Duration.ofSeconds(1)), next);
    assertFalse(policy.isDead(1));
  }

  @Test
  void backoffDoublesUntilCap() {
    assertEquals(NOW.plusSeconds(1), policy.nextAttemptAt(1));
    assertEquals(NOW.plusSeconds(2), policy.nextAttemptAt(2));
    assertEquals(NOW.plusSeconds(4), policy.nextAttemptAt(3));
    // would be 8s then cap — still within max
    OutboxRetryPolicy high = new OutboxRetryPolicy(10, 1000, 8000, Clock.fixed(NOW, ZoneOffset.UTC));
    assertEquals(NOW.plusSeconds(8), high.nextAttemptAt(4));
    assertEquals(NOW.plusSeconds(8), high.nextAttemptAt(5)); // capped
  }

  @Test
  void deadAfterMaxAttempts() {
    assertFalse(policy.isDead(2));
    assertTrue(policy.isDead(3));
    assertTrue(policy.isDead(4));
    assertEquals(3, policy.maxAttempts());
  }
}
