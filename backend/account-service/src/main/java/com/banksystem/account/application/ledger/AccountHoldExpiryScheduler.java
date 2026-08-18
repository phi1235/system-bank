package com.banksystem.account.application.ledger;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AccountHoldExpiryScheduler {
  private final AccountHoldService holdService;

  public AccountHoldExpiryScheduler(AccountHoldService holdService) {
    this.holdService = holdService;
  }

  @Scheduled(cron = "${bank.ledger.hold-expiry.cron}")
  public void expire() {
    holdService.expireDueHolds();
  }
}
