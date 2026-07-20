package com.banksystem.transaction.application;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.domain.TransferOrderRepository;
import com.banksystem.transaction.domain.TransferStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Application policy for internal transfer limits.
 * Keeps monetary thresholds out of controllers and easy to unit-test.
 */
@Component
public class TransferLimitPolicy {

  private final TransferOrderRepository transferOrderRepository;
  private final BigDecimal maxPerTransaction;
  private final BigDecimal dailyLimit;
  private final Clock clock;

  public TransferLimitPolicy(
      TransferOrderRepository transferOrderRepository,
      @Value("${bank.transfer.max-per-transaction:50000000}") BigDecimal maxPerTransaction,
      @Value("${bank.transfer.daily-limit:200000000}") BigDecimal dailyLimit,
      Clock clock) {
    this.transferOrderRepository = transferOrderRepository;
    this.maxPerTransaction = maxPerTransaction;
    this.dailyLimit = dailyLimit;
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

    Instant dayStart = LocalDate.now(clock).atStartOfDay().toInstant(ZoneOffset.UTC);
    BigDecimal spentToday = transferOrderRepository.sumAmountByUserAndStatusSince(
        userId, TransferStatus.COMPLETED, dayStart);
    if (spentToday == null) {
      spentToday = BigDecimal.ZERO;
    }
    BigDecimal projected = spentToday.add(amount);
    if (projected.compareTo(dailyLimit) > 0) {
      throw new BusinessException(
          "DAILY_LIMIT_EXCEEDED",
          "Transfer would exceed daily limit of " + dailyLimit.toPlainString()
              + " (spent today: " + spentToday.toPlainString() + ")",
          HttpStatus.UNPROCESSABLE_ENTITY);
    }
  }

  public BigDecimal maxPerTransaction() {
    return maxPerTransaction;
  }

  public BigDecimal dailyLimit() {
    return dailyLimit;
  }
}
