package com.banksystem.transaction.application.transfer;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.domain.transfer.SandboxTopupQuotaRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SandboxTopupGateService {

  private static final Logger log = LoggerFactory.getLogger(SandboxTopupGateService.class);
  public static final BigDecimal MAX_DAILY_QUOTA = new BigDecimal("50000000.00");
  private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

  private final boolean sandboxTopupEnabled;
  private final SandboxTopupQuotaRepository quotaRepository;

  public SandboxTopupGateService(
      @Value("${bank.sandbox.topup.enabled}") boolean sandboxTopupEnabled,
      SandboxTopupQuotaRepository quotaRepository) {
    this.sandboxTopupEnabled = sandboxTopupEnabled;
    this.quotaRepository = quotaRepository;
  }

  public boolean isSandboxTopupEnabled() {
    return sandboxTopupEnabled;
  }

  @Transactional
  public BigDecimal validateAndAccumulate(UUID userId, BigDecimal amount) {
    if (!sandboxTopupEnabled) {
      throw new BusinessException("SANDBOX_TOPUP_DISABLED", "Sandbox 1-Click Topup is disabled in this environment");
    }
    if (userId == null) {
      throw new BusinessException("UNAUTHORIZED", "Authenticated user identity required for sandbox topup");
    }
    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new BusinessException("INVALID_AMOUNT", "Topup amount must be strictly positive");
    }
    if (amount.compareTo(MAX_DAILY_QUOTA) > 0) {
      throw new BusinessException("SANDBOX_QUOTA_EXCEEDED", "Topup amount exceeds maximum single transaction quota (50,000,000 VND)");
    }

    LocalDate today = LocalDate.now(VIETNAM_ZONE);
    Optional<BigDecimal> result = quotaRepository.atomicAccumulateQuota(userId, today, amount, MAX_DAILY_QUOTA);

    if (result.isEmpty()) {
      log.warn("Daily sandbox topup quota exceeded: user={}, amount={}, date={}", userId, amount, today);
      throw new BusinessException("SANDBOX_QUOTA_EXCEEDED", "Daily sandbox topup quota (50,000,000 VND) has been reached for today");
    }

    log.info("Sandbox topup accumulated successfully: user={}, amount={}, totalToday={}", userId, amount, result.get());
    return result.get();
  }
}
