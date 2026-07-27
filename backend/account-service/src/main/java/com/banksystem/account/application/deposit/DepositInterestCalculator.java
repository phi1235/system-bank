package com.banksystem.account.application.deposit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Simple interest, ACT/365, rounded HALF_UP to 2 decimals:
 * {@code interest = principal * rateBps/10000 * days/365}.
 * Pure functions — no clock, no I/O; callers resolve banking-day dates.
 */
public final class DepositInterestCalculator {

  private static final BigDecimal BPS_DIVISOR = BigDecimal.valueOf(10_000);
  private static final BigDecimal DAYS_PER_YEAR = BigDecimal.valueOf(365);

  private DepositInterestCalculator() {}

  public static long daysBetween(LocalDate from, LocalDate to) {
    return Math.max(0, ChronoUnit.DAYS.between(from, to));
  }

  public static BigDecimal interest(BigDecimal principal, int rateBps, long days) {
    if (days <= 0 || rateBps <= 0) {
      return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
    return principal
        .multiply(BigDecimal.valueOf(rateBps))
        .multiply(BigDecimal.valueOf(days))
        .divide(BPS_DIVISOR.multiply(DAYS_PER_YEAR), 2, RoundingMode.HALF_UP);
  }
}
