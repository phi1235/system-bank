package com.banksystem.transaction.application.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.domain.OutboxStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxListQueryTest {

  @Test
  void defaultsStatusDeadAndClampsSize() {
    OutboxListQuery q = OutboxListQuery.of(null, null, null, null, null, null, null, -1, 500);
    assertEquals(OutboxStatus.DEAD, q.status());
    assertEquals(0, q.page());
    assertEquals(OutboxListQuery.MAX_SIZE, q.size());
    assertEquals(OutboxListQuery.EPOCH, q.from());
    assertEquals(OutboxListQuery.FAR_FUTURE, q.to());
    assertFalse(q.hasEventType());
    assertFalse(q.hasEventId());
    assertFalse(q.hasAggregateId());
    assertFalse(q.hasQ());
  }

  @Test
  void parsesStatusCaseInsensitiveAndFilters() {
    UUID eventId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UUID aggregateId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    OutboxListQuery q =
        OutboxListQuery.of(
            "pending",
            " TRANSACTION_COMPLETED ",
            eventId.toString(),
            aggregateId.toString(),
            " broker ",
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-12-31T00:00:00Z"),
            1,
            10);
    assertEquals(OutboxStatus.PENDING, q.status());
    assertEquals("TRANSACTION_COMPLETED", q.eventType());
    assertEquals(eventId, q.eventId());
    assertEquals(aggregateId, q.aggregateId());
    assertEquals("broker", q.q());
    assertTrue(q.hasEventType());
    assertTrue(q.hasEventId());
    assertTrue(q.hasAggregateId());
    assertTrue(q.hasQ());
    assertEquals(1, q.page());
    assertEquals(10, q.size());
  }

  @Test
  void blankFiltersBecomeAbsent() {
    OutboxListQuery q = OutboxListQuery.of("DEAD", " ", " ", " ", " ", null, null, 0, 20);
    assertNull(q.eventType());
    assertNull(q.eventId());
    assertNull(q.aggregateId());
    assertNull(q.q());
  }

  @Test
  void rejectsInvalidStatus() {
    BusinessException ex =
        assertThrows(
            BusinessException.class,
            () -> OutboxListQuery.of("BOGUS", null, null, null, null, null, null, 0, 10));
    assertEquals("INVALID_STATUS", ex.getCode());
  }

  @Test
  void rejectsInvalidDateRange() {
    BusinessException ex =
        assertThrows(
            BusinessException.class,
            () ->
                OutboxListQuery.of(
                    null,
                    null,
                    null,
                    null,
                    null,
                    Instant.parse("2026-12-31T00:00:00Z"),
                    Instant.parse("2026-01-01T00:00:00Z"),
                    0,
                    20));
    assertEquals("INVALID_DATE_RANGE", ex.getCode());
  }

  @Test
  void rejectsInvalidEventId() {
    BusinessException ex =
        assertThrows(
            BusinessException.class,
            () -> OutboxListQuery.of(null, null, "not-uuid", null, null, null, null, 0, 20));
    assertEquals("INVALID_OUTBOX_ID", ex.getCode());
  }

  @Test
  void rejectsInvalidAggregateId() {
    BusinessException ex =
        assertThrows(
            BusinessException.class,
            () -> OutboxListQuery.of(null, null, null, "bad", null, null, null, 0, 20));
    assertEquals("INVALID_AGGREGATE_ID", ex.getCode());
  }
}
