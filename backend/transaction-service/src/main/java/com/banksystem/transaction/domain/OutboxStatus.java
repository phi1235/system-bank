package com.banksystem.transaction.domain;

/**
 * Outbox delivery lifecycle.
 * PENDING  = eligible for publish (subject to next_attempt_at)
 * PUBLISHED = successfully sent to broker
 * DEAD     = exceeded max attempts (manual intervention / replay later)
 */
public enum OutboxStatus {
  PENDING,
  PUBLISHED,
  DEAD
}
