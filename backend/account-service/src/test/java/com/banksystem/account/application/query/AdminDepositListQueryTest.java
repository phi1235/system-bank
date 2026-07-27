package com.banksystem.account.application.deposit.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.banksystem.account.domain.enums.deposit.TermDepositStatus;
import com.banksystem.common.exception.BusinessException;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminDepositListQueryTest {

  @Test
  void defaultsUseSentinelsAndUnsetFlags() {
    AdminDepositListQuery q =
        AdminDepositListQuery.of(null, null, null, null, null, null, null, null, null);

    assertEquals(0, q.page());
    assertEquals(AdminDepositListQuery.DEFAULT_SIZE, q.size());
    assertFalse(q.hasStatus());
    assertFalse(q.hasProduct());
    assertFalse(q.hasUser());
    assertFalse(q.hasAccount());
    assertEquals(AdminDepositListQuery.MIN_DATE, q.maturityFrom());
    assertEquals(AdminDepositListQuery.MAX_DATE, q.maturityTo());
  }

  @Test
  void parsesFiltersAndCapsSize() {
    UUID user = UUID.randomUUID();
    AdminDepositListQuery q =
        AdminDepositListQuery.of(
            2, 500, "open", "TD6M", user.toString(), null, null,
            LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1));

    assertEquals(AdminDepositListQuery.MAX_SIZE, q.size());
    assertTrue(q.hasStatus());
    assertEquals(TermDepositStatus.OPEN, q.statusOrDefault());
    assertTrue(q.hasProduct());
    assertEquals("TD6M", q.productCodeOrEmpty());
    assertEquals(user, q.userIdOrNil());
  }

  @Test
  void rejectsUnknownStatus() {
    BusinessException ex =
        assertThrows(
            BusinessException.class,
            () -> AdminDepositListQuery.of(0, 20, "PENDING", null, null, null, null, null, null));
    assertEquals("DEPOSIT_STATUS_INVALID", ex.getCode());
  }

  @Test
  void rejectsMalformedUuidAndInvertedRange() {
    assertEquals(
        "INVALID_UUID",
        assertThrows(
                BusinessException.class,
                () -> AdminDepositListQuery.of(0, 20, null, null, "not-a-uuid", null, null, null, null))
            .getCode());
    assertEquals(
        "INVALID_DATE_RANGE",
        assertThrows(
                BusinessException.class,
                () ->
                    AdminDepositListQuery.of(
                        0, 20, null, null, null, null, null,
                        LocalDate.of(2026, 9, 1), LocalDate.of(2026, 8, 1)))
            .getCode());
  }
}
