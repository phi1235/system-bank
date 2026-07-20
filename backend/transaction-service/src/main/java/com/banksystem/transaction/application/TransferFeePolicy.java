package com.banksystem.transaction.application;

import com.banksystem.common.exception.BusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Application policy: compute internal-transfer fee from principal amount.
 * Keeps fee formula out of controllers/services; unit-testable.
 *
 * <p>Formula (all config-driven, defaults free):
 * {@code fee = clamp(min, max, flat + amount * percent / 100)} rounded to 2 decimals HALF_UP.
 * GL posting of fee to bank income account is handled by {@link TransferFeeGlService}.
 */
@Component
public class TransferFeePolicy {

  private final boolean enabled;
  private final BigDecimal flat;
  private final BigDecimal percent;
  private final BigDecimal minFee;
  private final BigDecimal maxFee;

  public TransferFeePolicy(
      @Value("${bank.transfer.fee.enabled:true}") boolean enabled,
      @Value("${bank.transfer.fee.flat:0}") BigDecimal flat,
      @Value("${bank.transfer.fee.percent:0}") BigDecimal percent,
      @Value("${bank.transfer.fee.min:0}") BigDecimal minFee,
      @Value("${bank.transfer.fee.max:50000}") BigDecimal maxFee) {
    this.enabled = enabled;
    this.flat = nullToZero(flat);
    this.percent = nullToZero(percent);
    this.minFee = nullToZero(minFee);
    this.maxFee = maxFee == null ? new BigDecimal("50000") : maxFee;
    if (this.flat.compareTo(BigDecimal.ZERO) < 0
        || this.percent.compareTo(BigDecimal.ZERO) < 0
        || this.minFee.compareTo(BigDecimal.ZERO) < 0
        || this.maxFee.compareTo(BigDecimal.ZERO) < 0) {
      throw new IllegalArgumentException("Transfer fee config values must be non-negative");
    }
  }

  /**
   * @param principal transfer amount (not including fee)
   * @return fee ≥ 0, scale 2
   */
  public BigDecimal calculate(BigDecimal principal) {
    if (principal == null || principal.compareTo(BigDecimal.ZERO) <= 0) {
      throw new BusinessException("INVALID_AMOUNT", "Amount must be positive", HttpStatus.BAD_REQUEST);
    }
    if (!enabled) {
      return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
    BigDecimal pctPart = principal
        .multiply(percent)
        .divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP);
    BigDecimal raw = flat.add(pctPart).setScale(2, RoundingMode.HALF_UP);
    if (raw.compareTo(minFee) < 0) {
      raw = minFee.setScale(2, RoundingMode.HALF_UP);
    }
    if (raw.compareTo(maxFee) > 0) {
      raw = maxFee.setScale(2, RoundingMode.HALF_UP);
    }
    return raw;
  }

  /** Total debit from source = principal + fee (scale 2). */
  public BigDecimal totalDebit(BigDecimal principal) {
    return principal.add(calculate(principal)).setScale(2, RoundingMode.HALF_UP);
  }

  public boolean enabled() {
    return enabled;
  }

  public BigDecimal flat() {
    return flat;
  }

  public BigDecimal percent() {
    return percent;
  }

  private static BigDecimal nullToZero(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v;
  }
}
