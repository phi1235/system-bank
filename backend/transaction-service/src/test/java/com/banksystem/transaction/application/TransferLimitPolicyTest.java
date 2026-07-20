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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransferLimitPolicyTest {

  private TransferOrderRepository repository;
  private TransferLimitPolicy policy;
  private final UUID userId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
  private final Clock clock = Clock.fixed(Instant.parse("2026-07-21T10:00:00Z"), ZoneOffset.UTC);

  @BeforeEach
  void setUp() {
    repository = mock(TransferOrderRepository.class);
    policy = new TransferLimitPolicy(
        repository,
        new BigDecimal("50000000"),
        new BigDecimal("200000000"),
        clock);
  }

  @Test
  void rejectsAbovePerTransactionLimit() {
    BusinessException ex = assertThrows(BusinessException.class,
        () -> policy.validate(userId, new BigDecimal("50000001")));
    assertEquals("TRANSFER_LIMIT_EXCEEDED", ex.getCode());
  }

  @Test
  void rejectsWhenDailyLimitWouldBeExceeded() {
    Instant dayStart = LocalDate.now(clock).atStartOfDay().toInstant(ZoneOffset.UTC);
    when(repository.sumAmountByUserAndStatusSince(eq(userId), eq(TransferStatus.COMPLETED), eq(dayStart)))
        .thenReturn(new BigDecimal("180000000"));

    BusinessException ex = assertThrows(BusinessException.class,
        () -> policy.validate(userId, new BigDecimal("30000000")));
    assertEquals("DAILY_LIMIT_EXCEEDED", ex.getCode());
  }

  @Test
  void allowsWithinLimits() {
    Instant dayStart = LocalDate.now(clock).atStartOfDay().toInstant(ZoneOffset.UTC);
    when(repository.sumAmountByUserAndStatusSince(eq(userId), eq(TransferStatus.COMPLETED), eq(dayStart)))
        .thenReturn(new BigDecimal("10000000"));

    assertDoesNotThrow(() -> policy.validate(userId, new BigDecimal("20000000")));
  }
}
