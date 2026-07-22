package com.banksystem.transaction.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.api.dto.TransferDtos.TransferQuoteResponse;
import com.banksystem.transaction.domain.AuditLogRepository;
import com.banksystem.transaction.domain.SagaStepLogRepository;
import com.banksystem.transaction.domain.TransferOrderRepository;
import com.banksystem.transaction.domain.TransferStatus;
import com.banksystem.transaction.infrastructure.feign.AccountClient;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransferServiceQuoteTest {

  private TransferOrderRepository transferOrderRepository;
  private TransferService service;
  private final UUID userId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
  private final Clock midDayClock =
      Clock.fixed(Instant.parse("2026-07-21T10:00:00Z"), ZoneOffset.UTC);

  @BeforeEach
  void setUp() {
    transferOrderRepository = mock(TransferOrderRepository.class);
    TransferLimitPolicy limitPolicy = new TransferLimitPolicy(
        transferOrderRepository,
        new BigDecimal("50000000"),
        new BigDecimal("200000000"),
        "Asia/Bangkok",
        midDayClock);
    TransferFeePolicy feePolicy = new TransferFeePolicy(
        true, new BigDecimal("1000"), new BigDecimal("0.1"), BigDecimal.ZERO, new BigDecimal("50000"));
    service = new TransferService(
        transferOrderRepository,
        mock(AuditLogRepository.class),
        mock(SagaStepLogRepository.class),
        mock(AccountClient.class),
        mock(TransferSagaOrchestrator.class),
        limitPolicy,
        feePolicy,
        "test-key");
  }

  @Test
  void quoteWithAmountReturnsFeeAndRemaining() {
    Instant dayStart = Instant.parse("2026-07-20T17:00:00Z");
    when(transferOrderRepository.sumAmountByUserAndStatusSince(
            eq(userId), eq(TransferStatus.COMPLETED), eq(dayStart)))
        .thenReturn(new BigDecimal("10000000"));

    TransferQuoteResponse q = service.quote(userId, new BigDecimal("1000000"));

    assertEquals(new BigDecimal("1000000"), q.amount());
    assertEquals(new BigDecimal("2000.00"), q.feeAmount());
    assertEquals(new BigDecimal("1002000.00"), q.totalDebit());
    assertEquals(new BigDecimal("50000000"), q.maxPerTransaction());
    assertEquals(new BigDecimal("200000000"), q.dailyLimit());
    assertEquals(new BigDecimal("10000000"), q.spentToday());
    assertEquals(new BigDecimal("190000000"), q.remainingToday());
    assertEquals("VND", q.currency());
    assertEquals("Asia/Bangkok", q.dailyLimitZone());
    assertTrue(q.feeEnabled());
    assertEquals(new BigDecimal("1000.00"), q.feeFlat());
    assertEquals(new BigDecimal("0.1"), q.feePercent());
    assertEquals(new BigDecimal("1000.00"), q.feePercentAmount());
    assertEquals(new BigDecimal("0.00"), q.feeMin());
    assertEquals(new BigDecimal("50000.00"), q.feeMax());
    assertEquals(new BigDecimal("2000.00"), q.feeRawBeforeClamp());
    assertEquals(false, q.feeCappedByMin());
    assertEquals(false, q.feeCappedByMax());
  }

  @Test
  void quoteWithoutAmountReturnsZeroFeeAndLimitsOnly() {
    Instant dayStart = Instant.parse("2026-07-20T17:00:00Z");
    when(transferOrderRepository.sumAmountByUserAndStatusSince(
            eq(userId), eq(TransferStatus.COMPLETED), eq(dayStart)))
        .thenReturn(null);

    TransferQuoteResponse q = service.quote(userId, null);

    assertEquals(0, q.amount().compareTo(BigDecimal.ZERO));
    assertEquals(new BigDecimal("0.00"), q.feeAmount());
    assertEquals(new BigDecimal("0.00"), q.totalDebit());
    assertEquals(0, q.spentToday().compareTo(BigDecimal.ZERO));
    assertEquals(new BigDecimal("200000000"), q.remainingToday());
  }

  @Test
  void quoteRejectsNegativeAmount() {
    BusinessException ex = assertThrows(BusinessException.class,
        () -> service.quote(userId, new BigDecimal("-1")));
    assertEquals("INVALID_AMOUNT", ex.getCode());
  }
}
