package com.banksystem.transaction.application.query;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.domain.OutboxStatus;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Admin outbox list filters + paging policy (kept out of controller).
 */
public record OutboxListQuery(
    OutboxStatus status,
    String eventType,
    UUID eventId,
    UUID aggregateId,
    String q,
    Instant from,
    Instant to,
    int page,
    int size) {

  public static final int DEFAULT_SIZE = 20;
  public static final int MAX_SIZE = 100;
  public static final Instant EPOCH = Instant.EPOCH;
  public static final Instant FAR_FUTURE = Instant.parse("9999-12-31T23:59:59Z");

  public static OutboxListQuery of(
      String status,
      String eventType,
      String eventId,
      String aggregateId,
      String q,
      Instant from,
      Instant to,
      Integer page,
      Integer size) {
    Instant fromTs = from == null ? EPOCH : from;
    Instant toTs = to == null ? FAR_FUTURE : to;
    if (fromTs.isAfter(toTs)) {
      throw new BusinessException(
          "INVALID_DATE_RANGE",
          "from must be before or equal to to",
          HttpStatus.BAD_REQUEST);
    }

    int p = page == null || page < 0 ? 0 : page;
    int s = size == null ? DEFAULT_SIZE : size;
    if (s < 1) {
      s = DEFAULT_SIZE;
    }
    if (s > MAX_SIZE) {
      s = MAX_SIZE;
    }

    return new OutboxListQuery(
        parseStatus(status),
        blankToNull(eventType),
        parseUuid(eventId, "INVALID_OUTBOX_ID", "eventId must be a valid UUID"),
        parseUuid(aggregateId, "INVALID_AGGREGATE_ID", "aggregateId must be a valid UUID"),
        blankToNull(q),
        fromTs,
        toTs,
        p,
        s);
  }

  public boolean hasEventType() {
    return eventType != null;
  }

  public boolean hasEventId() {
    return eventId != null;
  }

  public boolean hasAggregateId() {
    return aggregateId != null;
  }

  public boolean hasQ() {
    return q != null;
  }

  private static String blankToNull(String raw) {
    if (raw == null) {
      return null;
    }
    String t = raw.trim();
    return t.isEmpty() ? null : t;
  }

  private static OutboxStatus parseStatus(String raw) {
    if (raw == null || raw.isBlank()) {
      return OutboxStatus.DEAD;
    }
    try {
      return OutboxStatus.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      throw new BusinessException(
          "INVALID_STATUS",
          "status must be PENDING, PUBLISHED, or DEAD",
          HttpStatus.BAD_REQUEST);
    }
  }

  private static UUID parseUuid(String raw, String code, String message) {
    String t = blankToNull(raw);
    if (t == null) {
      return null;
    }
    try {
      return UUID.fromString(t);
    } catch (IllegalArgumentException ex) {
      throw new BusinessException(code, message, HttpStatus.BAD_REQUEST);
    }
  }
}
