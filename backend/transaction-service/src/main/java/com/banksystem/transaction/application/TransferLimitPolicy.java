package com.banksystem.transaction.application;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.domain.TransferOrderRepository;
import com.banksystem.transaction.domain.TransferStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Application policy for internal transfer limits.
 * Keeps monetary thresholds out of controllers and easy to unit-test.
 * Daily window is calendar-day in {@code bank.transfer.daily-limit-zone} (default Asia/Bangkok).
 */
@Component
public class TransferLimitPolicy {

  private final TransferOrderRepository transferOrderRepository;
  private final BigDecimal maxPerTransaction;
  private final BigDecimal dailyLimit;
  private final Clock clock;
  private final ZoneId dailyLimitZone;

  public TransferLimitPolicy(
      TransferOrderRepository transferOrderRepository,
      @Value("${bank.transfer.max-per-transaction:50000000}") BigDecimal maxPerTransaction,
      @Value("${bank.transfer.daily-limit:200000000}") BigDecimal dailyLimit,
      @Value("${bank.transfer.daily-limit-zone:Asia/Bangkok}") String dailyLimitZone,
      Clock clock) {
    this.transferOrderRepository = transferOrderRepository;
    this.maxPerTransaction = maxPerTransaction;
    this.dailyLimit = dailyLimit;
    this.dailyLimitZone = ZoneId.of(dailyLimitZone);
    this.clock = clock;
  }

  public void validate(UUID userId, BigDecimal amount) {
    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new BusinessException("INVALID_AMOUNT", "Amount must be positive", HttpStatus.BAD_REQUEST);
    }
    if (amount.compareTo(maxPerTransaction) > 0) {
      throw new BusinessException(
          "TRANSFER_LIMIT_EXCEEDED",
          "Amount exceeds per-transaction limit of " + maxPerTransaction.toPlainString(),
          HttpStatus.UNPROCESSABLE_ENTITY);
    }

    BigDecimal spentToday = spentToday(userId);
    BigDecimal projected = spentToday.add(amount);
    if (projected.compareTo(dailyLimit) > 0) {
      throw new BusinessException(
          "DAILY_LIMIT_EXCEEDED",
          "Transfer would exceed daily limit of " + dailyLimit.toPlainString()
              + " (spent today: " + spentToday.toPlainString() + ")",
          HttpStatus.UNPROCESSABLE_ENTITY);
    }
  }

  /** Sum of COMPLETED principal amounts since start of banking day (never null). */
  public BigDecimal spentToday(UUID userId) {
    Instant dayStart = startOfBusinessDay();
    BigDecimal spent = transferOrderRepository.sumAmountByUserAndStatusSince(
        userId, TransferStatus.COMPLETED, dayStart);
    return spent == null ? BigDecimal.ZERO : spent;
  }

  /** Remaining principal capacity for today: max(0, dailyLimit - spentToday). */
  public BigDecimal remainingToday(UUID userId) {
    BigDecimal remaining = dailyLimit.subtract(spentToday(userId));
    return remaining.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : remaining;
  }

  /**
   * Inclusive start of the current banking calendar day in the configured zone.
   * Example: Asia/Bangkok 2026-07-21 00:00+07 → Instant 2026-07-20T17:00:00Z.
   */
  Instant startOfBusinessDay() {
    LocalDate today = LocalDate.now(clock.withZone(dailyLimitZone));
    return today.atStartOfDay(dailyLimitZone).toInstant();
  }

  public BigDecimal maxPerTransaction() {
    return maxPerTransaction;
  }

  public BigDecimal dailyLimit() {
    return dailyLimit;
  }

  public ZoneId dailyLimitZone() {
    return dailyLimitZone;
  }
}
