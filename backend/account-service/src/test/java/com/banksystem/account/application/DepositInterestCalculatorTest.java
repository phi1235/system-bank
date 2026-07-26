package com.banksystem.account.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DepositInterestCalculatorTest {

  @Test
  void fullYearAtRateEqualsSimpleInterest() {
    // 1,000,000 * 5.30% * 365/365 = 53,000.00
    BigDecimal interest =
        DepositInterestCalculator.interest(new BigDecimal("1000000"), 530, 365);
    assertEquals(new BigDecimal("53000.00"), interest);
  }

  @Test
  void partialTermRoundsHalfUpToTwoDecimals() {
    // 1,000,000 * 3.00% * 30/365 = 2465.7534... -> 2465.75
    BigDecimal interest =
        DepositInterestCalculator.interest(new BigDecimal("1000000"), 300, 30);
    assertEquals(new BigDecimal("2465.75"), interest);
  }

  @Test
  void zeroDaysOrZeroRateYieldsZero() {
    assertEquals(
        new BigDecimal("0.00"),
        DepositInterestCalculator.interest(new BigDecimal("1000000"), 530, 0));
    assertEquals(
        new BigDecimal("0.00"),
        DepositInterestCalculator.interest(new BigDecimal("1000000"), 0, 365));
  }

  @Test
  void daysBetweenNeverNegative() {
    assertEquals(0, DepositInterestCalculator.daysBetween(
        LocalDate.of(2026, 7, 27), LocalDate.of(2026, 7, 26)));
    assertEquals(31, DepositInterestCalculator.daysBetween(
        LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 1)));
  }
}
