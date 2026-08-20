package com.banksystem.account.application.sweep;

import com.banksystem.account.api.dto.AutoSweepDtos.AutoSweepBatchResponse;
import com.banksystem.account.domain.sweep.AutoSweepProfileRepository;
import com.banksystem.account.infrastructure.sweep.AutoSweepBatchRunRepository;
import com.banksystem.common.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AutoSweepBatchService {
  private static final Logger log = LoggerFactory.getLogger(AutoSweepBatchService.class);
  private final AutoSweepProfileRepository profileRepository;
  private final AutoSweepMovementService movementService;
  private final AutoSweepBatchRunRepository batchRunRepository;
  private final TransactionTemplate transactionTemplate;
  private final Clock clock;
  private final ZoneId zone;
  private final int pageSize;
  private final long leaseSeconds;
  private final String workerId = "auto-sweep-" + UUID.randomUUID();

  public AutoSweepBatchService(
      AutoSweepProfileRepository profileRepository,
      AutoSweepMovementService movementService,
      AutoSweepBatchRunRepository batchRunRepository,
      TransactionTemplate transactionTemplate,
      Clock clock,
      @Value("${bank.deposit.zone}") String zone,
      @Value("${bank.auto-sweep.batch.page-size}") int pageSize,
      @Value("${bank.auto-sweep.batch.lease-seconds}") long leaseSeconds) {
    if (pageSize < 1 || leaseSeconds < 1) {
      throw new IllegalArgumentException("Auto-sweep batch page size and lease must be positive");
    }
    this.profileRepository = profileRepository;
    this.movementService = movementService;
    this.batchRunRepository = batchRunRepository;
    this.transactionTemplate = transactionTemplate;
    this.clock = clock;
    this.zone = ZoneId.of(zone);
    this.pageSize = pageSize;
    this.leaseSeconds = leaseSeconds;
  }

  public AutoSweepBatchResponse run() {
    LocalDate date = LocalDate.now(clock.withZone(zone));
    UUID runId = transactionTemplate.execute(status -> claim(date));
    if (runId == null) {
      return new AutoSweepBatchResponse(date, 0, 0, BigDecimal.ZERO, false);
    }
    int processed = 0;
    int failed = 0;
    BigDecimal total = BigDecimal.ZERO;
    String lastError = null;
    int page = 0;
    while (true) {
      List<UUID> profileIds = profileRepository.findEnabledIds(PageRequest.of(page, pageSize));
      for (UUID profileId : profileIds) {
        try {
          total = total.add(movementService.processEndOfDay(profileId, date));
          processed++;
        } catch (Exception exception) {
          failed++;
          lastError = exception.getMessage();
          log.error("Auto-sweep EOD failed for profile {}", profileId, exception);
        }
      }
      if (profileIds.size() < pageSize) break;
      page++;
    }
    int finalProcessed = processed;
    int finalFailed = failed;
    BigDecimal finalTotal = total;
    String finalError = lastError;
    transactionTemplate.executeWithoutResult(status -> finish(
        runId, finalProcessed, finalFailed, finalTotal, finalError));
    return new AutoSweepBatchResponse(date, processed, failed, total, true);
  }

  private UUID claim(LocalDate date) {
    Instant now = clock.instant();
    return batchRunRepository.claim(
        date, workerId, now, now.plusSeconds(leaseSeconds)).orElse(null);
  }

  private void finish(UUID runId, int processed, int failed, BigDecimal total, String lastError) {
    Instant completedAt = clock.instant();
    if (!batchRunRepository.finish(
        runId, workerId, processed, failed, total, lastError, completedAt)) {
      throw new BusinessException(
          "AUTO_SWEEP_BATCH_LEASE_LOST", "Auto-sweep batch lease was lost before completion");
    }
  }
}
