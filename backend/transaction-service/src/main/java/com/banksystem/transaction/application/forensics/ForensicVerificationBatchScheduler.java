package com.banksystem.transaction.application.forensics;

import com.banksystem.transaction.application.forensics.ForensicBatchLeaseService.BatchWindow;
import com.banksystem.transaction.domain.transfer.TransferOrderEntity;
import com.banksystem.transaction.domain.transfer.TransferOrderRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "bank.forensics.batch.enabled", havingValue = "true")
public class ForensicVerificationBatchScheduler {
  private static final Logger log = LoggerFactory.getLogger(ForensicVerificationBatchScheduler.class);
  private final ForensicBatchLeaseService leaseService;
  private final ForensicVerificationService verificationService;
  private final TransferOrderRepository transferRepository;
  private final UUID actor;
  private final int batchSize;
  private final Duration overlap;
  private final Duration lease;
  private final ForensicTelemetry telemetry;

  public ForensicVerificationBatchScheduler(
      ForensicBatchLeaseService leaseService,
      ForensicVerificationService verificationService,
      TransferOrderRepository transferRepository,
      @Value("${bank.forensics.batch.actor-id}") UUID actor,
      @Value("${bank.forensics.batch.size}") int batchSize,
      @Value("${bank.forensics.batch.overlap}") Duration overlap,
      @Value("${bank.forensics.batch.lease}") Duration lease,
      ForensicTelemetry telemetry) {
    this.leaseService = leaseService;
    this.verificationService = verificationService;
    this.transferRepository = transferRepository;
    this.actor = actor;
    this.batchSize = Math.max(1, Math.min(batchSize, 1000));
    this.overlap = overlap;
    this.lease = lease;
    this.telemetry = telemetry;
  }

  @Scheduled(cron = "${bank.forensics.batch.cron}")
  public void run() {
    UUID owner = UUID.randomUUID();
    BatchWindow window = leaseService.claim(owner, lease, overlap);
    if (window == null) return;
    try {
      List<TransferOrderEntity> transfers = transferRepository
          .findByUpdatedAtGreaterThanEqualAndUpdatedAtLessThanEqualOrderByUpdatedAtAscIdAsc(
              window.fromInclusive(), window.cutoff(), PageRequest.of(0, batchSize))
          .getContent();
      Instant watermark = window.cutoff();
      for (TransferOrderEntity transfer : transfers) {
        verificationService.check(
            transfer.getId(), actor,
            "batch:v1:" + transfer.getId() + ":" + transfer.getUpdatedAt().toEpochMilli());
        watermark = transfer.getUpdatedAt();
      }
      leaseService.complete(owner, transfers.size() < batchSize ? window.cutoff() : watermark);
      telemetry.batch(transfers.size(), true);
    } catch (Exception exception) {
      log.error("Forensic verification batch failed", exception);
      leaseService.fail(owner, exception.getMessage());
      telemetry.batch(0, false);
    }
  }
}
