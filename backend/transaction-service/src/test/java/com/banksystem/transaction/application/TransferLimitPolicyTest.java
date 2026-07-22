package com.banksystem.transaction.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.domain.TransferOrderRepository;
import com.banksystem.transaction.domain.TransferStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransferLimitPolicyTest {

  private static final String ZONE = "Asia/Bangkok";

  private TransferOrderRepository repository;
  private TransferLimitPolicy policy;
  private final UUID userId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

  /** 2026-07-21 17:00 Bangkok = 2026-07-21 10:00Z — mid-day VN. */
  private final Clock midDayClock = Clock.fixed(Instant.parse("2026-07-21T10:00:00Z"), ZoneOffset.UTC);

  /**
   * 2026-07-21 03:00 Bangkok = 2026-07-20 20:00Z — still "21 Jul" in VN,
   * but already "20 Jul" if day boundary were wrongly computed in UTC.
   */
  private final Clock afterUtcMidnightClock =
      Clock.fixed(Instant.parse("2026-07-20T20:00:00Z"), ZoneOffset.UTC);

  @BeforeEach
  void setUp() {
    repository = mock(TransferOrderRepository.class);
    policy = new TransferLimitPolicy(
        repository,
        new BigDecimal("50000000"),
        new BigDecimal("200000000"),
        ZONE,
        midDayClock);
  }

  @Test
  void rejectsAbovePerTransactionLimit() {
    BusinessException ex = assertThrows(BusinessException.class,
        () -> policy.validate(userId, new BigDecimal("50000001")));
    assertEquals("TRANSFER_LIMIT_EXCEEDED", ex.getCode());
  }

  @Test
  void rejectsWhenDailyLimitWouldBeExceeded() {
    // Bangkok day start for 2026-07-21 = 2026-07-20T17:00:00Z
    Instant dayStart = Instant.parse("2026-07-20T17:00:00Z");
    when(repository.sumAmountByUserAndStatusSince(eq(userId), eq(TransferStatus.COMPLETED), eq(dayStart)))
        .thenReturn(new BigDecimal("180000000"));

    BusinessException ex = assertThrows(BusinessException.class,
        () -> policy.validate(userId, new BigDecimal("30000000")));
    assertEquals("DAILY_LIMIT_EXCEEDED", ex.getCode());
  }

  @Test
  void allowsWithinLimits() {
    Instant dayStart = Instant.parse("2026-07-20T17:00:00Z");
    when(repository.sumAmountByUserAndStatusSince(eq(userId), eq(TransferStatus.COMPLETED), eq(dayStart)))
        .thenReturn(new BigDecimal("10000000"));

    assertDoesNotThrow(() -> policy.validate(userId, new BigDecimal("20000000")));
  }

  @Test
  void businessDayStartsAtBangkokMidnightNotUtc() {
    TransferLimitPolicy bangkokPolicy = new TransferLimitPolicy(
        repository,
        new BigDecimal("50000000"),
        new BigDecimal("200000000"),
        ZONE,
        afterUtcMidnightClock);

    // At 20:00Z on 20 Jul (= 03:00 Bangkok 21 Jul) banking day is 21 Jul Bangkok.
    assertEquals(Instant.parse("2026-07-20T17:00:00Z"), bangkokPolicy.startOfBusinessDay());

    // Wrong UTC-day start would have been 2026-07-20T00:00:00Z — ensure we query Bangkok start.
    Instant bangkokDayStart = Instant.parse("2026-07-20T17:00:00Z");
    when(repository.sumAmountByUserAndStatusSince(
            eq(userId), eq(TransferStatus.COMPLETED), eq(bangkokDayStart)))
        .thenReturn(new BigDecimal("190000000"));

    BusinessException ex = assertThrows(BusinessException.class,
        () -> bangkokPolicy.validate(userId, new BigDecimal("20000000")));
    assertEquals("DAILY_LIMIT_EXCEEDED", ex.getCode());
  }

  @Test
  void exposesConfiguredZone() {
    assertEquals(ZONE, policy.dailyLimitZone().getId());
  }

  @Test
  void remainingTodayNeverNegative() {
    Instant dayStart = Instant.parse("2026-07-20T17:00:00Z");
    when(repository.sumAmountByUserAndStatusSince(eq(userId), eq(TransferStatus.COMPLETED), eq(dayStart)))
        .thenReturn(new BigDecimal("250000000"));

    assertEquals(new BigDecimal("250000000"), policy.spentToday(userId));
    assertEquals(0, policy.remainingToday(userId).compareTo(BigDecimal.ZERO));
  }

  @Test
  void remainingTodaySubtractsSpent() {
    Instant dayStart = Instant.parse("2026-07-20T17:00:00Z");
    when(repository.sumAmountByUserAndStatusSince(eq(userId), eq(TransferStatus.COMPLETED), eq(dayStart)))
        .thenReturn(new BigDecimal("30000000"));

    assertEquals(new BigDecimal("170000000"), policy.remainingToday(userId));
  }
}
