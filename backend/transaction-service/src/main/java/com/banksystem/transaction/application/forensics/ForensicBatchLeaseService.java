package com.banksystem.transaction.application.forensics;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.domain.forensics.ForensicVerificationWatermarkEntity;
import com.banksystem.transaction.domain.forensics.ForensicVerificationWatermarkRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ForensicBatchLeaseService {
  static final String JOB_NAME = "TRANSACTION_VERIFICATION";
  private final ForensicVerificationWatermarkRepository repository;
  private final Clock clock;

  public ForensicBatchLeaseService(
      ForensicVerificationWatermarkRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Transactional
  public BatchWindow claim(UUID owner, Duration lease, Duration overlap) {
    ForensicVerificationWatermarkEntity state = repository.lockByJobName(JOB_NAME)
        .orElseThrow(() -> new BusinessException(
            "FORENSIC_BATCH_STATE_MISSING", "Verification watermark is not initialized"));
    Instant now = clock.instant();
    if (!state.claim(owner, now, now.plus(lease))) return null;
    repository.save(state);
    return new BatchWindow(state.getWatermark().minus(overlap), now, state.getWatermark());
  }

  @Transactional
  public void complete(UUID owner, Instant watermark) {
    ForensicVerificationWatermarkEntity state = repository.lockByJobName(JOB_NAME).orElseThrow();
    state.complete(owner, watermark, clock.instant());
    repository.save(state);
  }

  @Transactional
  public void fail(UUID owner, String error) {
    ForensicVerificationWatermarkEntity state = repository.lockByJobName(JOB_NAME).orElseThrow();
    state.fail(owner, error, clock.instant());
    repository.save(state);
  }

  public record BatchWindow(Instant fromInclusive, Instant cutoff, Instant previousWatermark) {}
}
