package com.banksystem.transaction.api.dto;

import java.time.Instant;

public final class OutboxDtos {
  private OutboxDtos() {}

  public record OutboxEventResponse(
      String id,
      String aggregateType,
      String aggregateId,
      String eventType,
      String status,
      int attemptCount,
      Instant nextAttemptAt,
      Instant createdAt,
      Instant publishedAt,
      String lastError
  ) {}

  public record OutboxCountsResponse(
      long pending,
      long published,
      long dead
  ) {}
}
