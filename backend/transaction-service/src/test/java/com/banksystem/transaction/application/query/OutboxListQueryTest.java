package com.banksystem.transaction.application.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.domain.OutboxStatus;
import org.junit.jupiter.api.Test;

class OutboxListQueryTest {

  @Test
  void defaultsStatusDeadAndClampsSize() {
    OutboxListQuery q = OutboxListQuery.of(null, -1, 500);
    assertEquals(OutboxStatus.DEAD, q.status());
    assertEquals(0, q.page());
    assertEquals(OutboxListQuery.MAX_SIZE, q.size());
  }

  @Test
  void parsesStatusCaseInsensitive() {
    assertEquals(OutboxStatus.PENDING, OutboxListQuery.of("pending", 0, 10).status());
  }

  @Test
  void rejectsInvalidStatus() {
    BusinessException ex = assertThrows(BusinessException.class,
        () -> OutboxListQuery.of("BOGUS", 0, 10));
    assertEquals("INVALID_STATUS", ex.getCode());
  }
}
