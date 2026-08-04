package com.banksystem.transaction.application.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.banksystem.common.exception.BusinessException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditListQueryTest {

  @Test
  void defaultsPageSizeAndDateBounds() {
    AuditListQuery q = AuditListQuery.of(null, null, null, null, null, null, -1, 5000);
    assertEquals(0, q.page());
    assertEquals(AuditListQuery.MAX_SIZE, q.size());
    assertEquals(AuditListQuery.EPOCH, q.from());
    assertEquals(AuditListQuery.FAR_FUTURE, q.to());
    assertFalse(q.hasAction());
    assertFalse(q.hasActor());
  }

  @Test
  void trimsFiltersAndParsesActor() {
    UUID actor = UUID.fromString("11111111-1111-1111-1111-111111111111");
    AuditListQuery q =
        AuditListQuery.of(
            " TRANSFER_CREATE ",
            " transfer ",
            actor.toString(),
            " abcd ",
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-12-31T00:00:00Z"),
            1,
            10);
    assertEquals("TRANSFER_CREATE", q.action());
    assertEquals("transfer", q.resourceType());
    assertEquals(actor, q.actorUserId());
    assertEquals("abcd", q.resourceId());
    assertTrue(q.hasAction());
    assertTrue(q.hasResourceType());
    assertTrue(q.hasActor());
    assertTrue(q.hasResourceId());
    assertEquals(1, q.page());
    assertEquals(10, q.size());
  }

  @Test
  void blankFiltersBecomeAbsent() {
    AuditListQuery q = AuditListQuery.of("  ", "", " ", null, null, null, 0, 20);
    assertNull(q.action());
    assertNull(q.resourceType());
    assertNull(q.actorUserId());
    assertFalse(q.hasAction());
    assertFalse(q.hasResourceType());
    assertFalse(q.hasActor());
  }

  @Test
  void rejectsInvalidDateRange() {
    BusinessException ex =
        assertThrows(
            BusinessException.class,
            () ->
                AuditListQuery.of(
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
  void rejectsInvalidActorUuid() {
    BusinessException ex =
        assertThrows(
            BusinessException.class,
            () -> AuditListQuery.of(null, null, "not-a-uuid", null, null, null, 0, 20));
    assertEquals("INVALID_ACTOR_ID", ex.getCode());
  }
}
