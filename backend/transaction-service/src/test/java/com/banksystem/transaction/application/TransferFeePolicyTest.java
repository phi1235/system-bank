package com.banksystem.transaction.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.banksystem.common.exception.BusinessException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TransferFeePolicyTest {

  @Test
  void defaultsToZeroWhenFlatAndPercentZero() {
    TransferFeePolicy policy = new TransferFeePolicy(
        true, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("50000"));
    assertEquals(new BigDecimal("0.00"), policy.calculate(new BigDecimal("1000000")));
    assertEquals(new BigDecimal("1000000.00"), policy.totalDebit(new BigDecimal("1000000")));
  }

  @Test
  void disabledAlwaysZero() {
    TransferFeePolicy policy = new TransferFeePolicy(
        false, new BigDecimal("1000"), new BigDecimal("1"), BigDecimal.ZERO, new BigDecimal("50000"));
    assertEquals(new BigDecimal("0.00"), policy.calculate(new BigDecimal("1000000")));
  }

  @Test
  void flatPlusPercent() {
    // flat 1000 + 0.1% of 1_000_000 = 1000 + 1000 = 2000
    TransferFeePolicy policy = new TransferFeePolicy(
        true, new BigDecimal("1000"), new BigDecimal("0.1"), BigDecimal.ZERO, new BigDecimal("50000"));
    assertEquals(new BigDecimal("2000.00"), policy.calculate(new BigDecimal("1000000")));
    assertEquals(new BigDecimal("1002000.00"), policy.totalDebit(new BigDecimal("1000000")));
  }

  @Test
  void respectsMinAndMax() {
    TransferFeePolicy minPolicy = new TransferFeePolicy(
        true, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("500"), new BigDecimal("50000"));
    assertEquals(new BigDecimal("500.00"), minPolicy.calculate(new BigDecimal("100")));

    TransferFeePolicy maxPolicy = new TransferFeePolicy(
        true, new BigDecimal("100000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("50000"));
    assertEquals(new BigDecimal("50000.00"), maxPolicy.calculate(new BigDecimal("1000000")));
  }

  @Test
  void rejectsNonPositivePrincipal() {
    TransferFeePolicy policy = new TransferFeePolicy(
        true, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("50000"));
    BusinessException ex = assertThrows(BusinessException.class,
        () -> policy.calculate(BigDecimal.ZERO));
    assertEquals("INVALID_AMOUNT", ex.getCode());
  }

  @Test
  void breakdownMatchesCalculateAndExposesParts() {
    TransferFeePolicy policy = new TransferFeePolicy(
        true, new BigDecimal("1000"), new BigDecimal("0.1"), BigDecimal.ZERO, new BigDecimal("50000"));
    TransferFeePolicy.FeeBreakdown b = policy.breakdown(new BigDecimal("1000000"));
    assertEquals(new BigDecimal("2000.00"), b.feeAmount());
    assertEquals(new BigDecimal("2000.00"), policy.calculate(new BigDecimal("1000000")));
    assertEquals(new BigDecimal("1000.00"), b.flat());
    assertEquals(new BigDecimal("0.1"), b.percent());
    assertEquals(new BigDecimal("1000.00"), b.percentAmount());
    assertEquals(new BigDecimal("2000.00"), b.rawBeforeClamp());
    assertEquals(false, b.cappedByMin());
    assertEquals(false, b.cappedByMax());
    assertEquals(true, b.enabled());
  }

  @Test
  void breakdownReportsMinAndMaxCaps() {
    TransferFeePolicy minPolicy = new TransferFeePolicy(
        true, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("500"), new BigDecimal("50000"));
    TransferFeePolicy.FeeBreakdown minB = minPolicy.breakdown(new BigDecimal("100"));
    assertEquals(new BigDecimal("500.00"), minB.feeAmount());
    assertEquals(true, minB.cappedByMin());
    assertEquals(false, minB.cappedByMax());

    TransferFeePolicy maxPolicy = new TransferFeePolicy(
        true, new BigDecimal("100000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("50000"));
    TransferFeePolicy.FeeBreakdown maxB = maxPolicy.breakdown(new BigDecimal("1000000"));
    assertEquals(new BigDecimal("50000.00"), maxB.feeAmount());
    assertEquals(false, maxB.cappedByMin());
    assertEquals(true, maxB.cappedByMax());
  }

  @Test
  void breakdownWhenDisabledIsZero() {
    TransferFeePolicy policy = new TransferFeePolicy(
        false, new BigDecimal("1000"), new BigDecimal("1"), BigDecimal.ZERO, new BigDecimal("50000"));
    TransferFeePolicy.FeeBreakdown b = policy.breakdown(new BigDecimal("1000000"));
    assertEquals(false, b.enabled());
    assertEquals(new BigDecimal("0.00"), b.feeAmount());
    assertEquals(new BigDecimal("0.00"), b.percentAmount());
  }
}
