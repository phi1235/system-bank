package com.banksystem.account.application.ledger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "bank.ledger.snapshot.enabled", havingValue = "true")
public class AccountTemporalSnapshotScheduler {
  private final AccountTemporalSnapshotService service;
  private final int batchSize;

  public AccountTemporalSnapshotScheduler(
      AccountTemporalSnapshotService service,
      @Value("${bank.ledger.snapshot.batch-size}") int batchSize) {
    this.service = service;
    this.batchSize = Math.max(1, Math.min(batchSize, 1000));
  }

  @Scheduled(cron = "${bank.ledger.snapshot.cron}")
  public void capture() {
    int page = 0;
    while (service.capturePage(page++, batchSize) == batchSize) {
      // Continue with a bounded page until the current account set is covered.
    }
  }
}
