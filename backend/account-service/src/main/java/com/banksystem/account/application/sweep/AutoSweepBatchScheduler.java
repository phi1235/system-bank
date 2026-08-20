package com.banksystem.account.application.sweep;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "bank.auto-sweep.batch.enabled", havingValue = "true")
public class AutoSweepBatchScheduler {
  private final AutoSweepBatchService batchService;

  public AutoSweepBatchScheduler(AutoSweepBatchService batchService) {
    this.batchService = batchService;
  }

  @Scheduled(cron = "${bank.auto-sweep.batch.cron}", zone = "${bank.deposit.zone}")
  public void nightly() {
    batchService.run();
  }
}

