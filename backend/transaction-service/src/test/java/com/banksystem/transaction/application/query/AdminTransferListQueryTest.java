package com.banksystem.transaction.application.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.domain.TransferStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminTransferListQueryTest {

  @Test
  void defaultsPageSizeAndDateBounds() {
    AdminTransferListQuery q =
        AdminTransferListQuery.of(null, null, null, null, null, -1, 5000);
    assertEquals(0, q.page());
    assertEquals(AdminTransferListQuery.MAX_SIZE, q.size());
    assertEquals(AdminTransferListQuery.EPOCH, q.from());
    assertEquals(AdminTransferListQuery.FAR_FUTURE, q.to());
    assertFalse(q.hasStatus());
    assertFalse(q.hasTransferId());
    assertFalse(q.hasQ());
  }

  @Test
  void parsesFilters() {
    UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
    AdminTransferListQuery q =
        AdminTransferListQuery.of(
            " completed ",
            id.toString(),
            "  1234  ",
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-12-31T00:00:00Z"),
            1,
            10);
    assertEquals(TransferStatus.COMPLETED, q.status());
    assertEquals(id, q.transferId());
    assertEquals("1234", q.q());
    assertTrue(q.hasStatus());
    assertTrue(q.hasTransferId());
    assertTrue(q.hasQ());
    assertEquals(1, q.page());
    assertEquals(10, q.size());
  }

  @Test
  void blankFiltersBecomeAbsent() {
    AdminTransferListQuery q = AdminTransferListQuery.of(" ", " ", " ", null, null, 0, 20);
    assertNull(q.status());
    assertNull(q.transferId());
    assertNull(q.q());
  }

  @Test
  void rejectsInvalidDateRange() {
    BusinessException ex =
        assertThrows(
            BusinessException.class,
            () ->
                AdminTransferListQuery.of(
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
  void rejectsInvalidStatus() {
    BusinessException ex =
        assertThrows(
            BusinessException.class,
            () -> AdminTransferListQuery.of("WEIRD", null, null, null, null, 0, 20));
    assertEquals("INVALID_STATUS", ex.getCode());
  }

  @Test
  void rejectsInvalidTransferId() {
    BusinessException ex =
        assertThrows(
            BusinessException.class,
            () -> AdminTransferListQuery.of(null, "not-uuid", null, null, null, 0, 20));
    assertEquals("INVALID_TRANSFER_ID", ex.getCode());
  }
}
