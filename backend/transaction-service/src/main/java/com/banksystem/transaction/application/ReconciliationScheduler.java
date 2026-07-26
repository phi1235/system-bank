package com.banksystem.transaction.application;

import com.banksystem.transaction.domain.ReconRunEntity;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly reconciliation of the previous banking day. Off by default; enable with
 * {@code RECON_ENABLED=true} (see {@code bank.recon.*} in application.yml).
 */
@Component
@ConditionalOnProperty(value = "bank.recon.enabled", havingValue = "true")
public class ReconciliationScheduler {

  private static final Logger log = LoggerFactory.getLogger(ReconciliationScheduler.class);

  private final ReconciliationService service;
  private final Clock clock;
  private final ZoneId zone;

  public ReconciliationScheduler(
      ReconciliationService service,
      Clock clock,
      @Value("${bank.transfer.daily-limit-zone}") String zone) {
    this.service = service;
    this.clock = clock;
    this.zone = ZoneId.of(zone);
  }

  @Scheduled(cron = "${bank.recon.cron}", zone = "${bank.transfer.daily-limit-zone}")
  public void reconcilePreviousBankingDay() {
    LocalDate date = LocalDate.now(clock.withZone(zone)).minusDays(1);
    log.info("Scheduled reconciliation starting for {}", date);
    service.runForDate(date, ReconRunEntity.TRIGGER_SCHEDULED);
  }
}
