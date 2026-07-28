package com.banksystem.account.application.deposit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly accrual + maturity for term deposits. Off by default; enable with
 * {@code DEPOSIT_BATCH_ENABLED=true}. Manual runs via the admin API always work.
 */
@Component
@ConditionalOnProperty(value = "bank.deposit.batch.enabled", havingValue = "true")
public class DepositBatchScheduler {

  private static final Logger log = LoggerFactory.getLogger(DepositBatchScheduler.class);

  private final DepositBatchService service;

  public DepositBatchScheduler(DepositBatchService service) {
    this.service = service;
  }

  @Scheduled(cron = "${bank.deposit.batch.cron}", zone = "${bank.deposit.zone}")
  public void nightly() {
    log.info("Scheduled deposit batch starting");
    service.run();
  }
}
