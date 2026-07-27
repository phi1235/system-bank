package com.banksystem.account.application.account.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.banksystem.account.domain.enums.account.LedgerEntryType;
import com.banksystem.common.exception.BusinessException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LedgerStatementQueryTest {

  private final UUID accountId = UUID.fromString("11111111-1111-1111-1111-111111111111");

  @Test
  void defaultsPageAndSize() {
    LedgerStatementQuery q = LedgerStatementQuery.of(accountId, null, null, null, null, null);
    assertEquals(0, q.page());
    assertEquals(LedgerStatementQuery.DEFAULT_SIZE, q.size());
    assertNull(q.entryType());
  }

  @Test
  void clampsMaxSize() {
    LedgerStatementQuery q = LedgerStatementQuery.of(accountId, 1, 500, "debit", null, null);
    assertEquals(1, q.page());
    assertEquals(LedgerStatementQuery.MAX_SIZE, q.size());
    assertEquals(LedgerEntryType.DEBIT, q.entryType());
  }

  @Test
  void rejectsInvalidEntryType() {
    BusinessException ex = assertThrows(BusinessException.class,
        () -> LedgerStatementQuery.of(accountId, 0, 20, "MOVE", null, null));
    assertEquals("INVALID_ENTRY_TYPE", ex.getCode());
  }

  @Test
  void rejectsInvertedDateRange() {
    Instant from = Instant.parse("2026-07-21T00:00:00Z");
    Instant to = Instant.parse("2026-07-20T00:00:00Z");
    BusinessException ex = assertThrows(BusinessException.class,
        () -> LedgerStatementQuery.of(accountId, 0, 20, null, from, to));
    assertEquals("INVALID_DATE_RANGE", ex.getCode());
  }
}
