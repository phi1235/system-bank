package com.banksystem.transaction.api.dto;

import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;

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
      String lastError,
      /** Present on detail; null on list rows to keep pages light. */
      String payload
  ) {}

  public record OutboxCountsResponse(
      long pending,
      long published,
      long dead
  ) {}

  public record AdminOutboxFilterRequest(
      String status,
      String eventType,
      String eventId,
      String aggregateId,
      String q,
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      Integer page,
      Integer size
  ) {}
}
