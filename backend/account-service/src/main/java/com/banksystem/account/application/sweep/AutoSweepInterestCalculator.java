package com.banksystem.account.application.sweep;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class AutoSweepInterestCalculator {
  private static final BigDecimal BPS_PER_YEAR = new BigDecimal("3650000");

  private AutoSweepInterestCalculator() {}

  public static BigDecimal calculate(BigDecimal principal, int annualRateBps, long days) {
    if (principal == null || principal.signum() <= 0 || annualRateBps <= 0 || days <= 0) {
      return BigDecimal.ZERO.setScale(2);
    }
    return principal.multiply(BigDecimal.valueOf(annualRateBps))
        .multiply(BigDecimal.valueOf(days))
        .divide(BPS_PER_YEAR, 2, RoundingMode.HALF_UP);
  }
}

