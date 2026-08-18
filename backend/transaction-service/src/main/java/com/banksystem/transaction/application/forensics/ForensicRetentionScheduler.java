package com.banksystem.transaction.application.forensics;

import com.banksystem.transaction.domain.forensics.ForensicCopilotSessionRepository;
import com.banksystem.transaction.domain.forensics.ForensicExportJobEntity;
import com.banksystem.transaction.domain.forensics.ForensicExportJobRepository;
import com.banksystem.transaction.domain.forensics.ForensicGraphCacheRepository;
import com.banksystem.transaction.domain.forensics.ForensicReplayRunEntity;
import com.banksystem.transaction.domain.forensics.ForensicReplayRunRepository;
import com.banksystem.transaction.domain.forensics.ForensicTwinForkEntity;
import com.banksystem.transaction.domain.forensics.ForensicTwinForkRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "bank.forensics.enabled", havingValue = "true")
public class ForensicRetentionScheduler {
  private static final Logger log = LoggerFactory.getLogger(ForensicRetentionScheduler.class);
  private final ForensicExportJobRepository exportRepository;
  private final ForensicTwinForkRepository forkRepository;
  private final ForensicReplayRunRepository replayRepository;
  private final ForensicCopilotSessionRepository sessionRepository;
  private final ForensicGraphCacheRepository graphRepository;
  private final ForensicArtifactStorage storage;
  private final Clock clock;

  public ForensicRetentionScheduler(
      ForensicExportJobRepository exportRepository,
      ForensicTwinForkRepository forkRepository,
      ForensicReplayRunRepository replayRepository,
      ForensicCopilotSessionRepository sessionRepository,
      ForensicGraphCacheRepository graphRepository,
      ForensicArtifactStorage storage,
      Clock clock) {
    this.exportRepository = exportRepository;
    this.forkRepository = forkRepository;
    this.replayRepository = replayRepository;
    this.sessionRepository = sessionRepository;
    this.graphRepository = graphRepository;
    this.storage = storage;
    this.clock = clock;
  }

  @Scheduled(cron = "${bank.forensics.retention-cron}")
  public void cleanup() {
    Instant now = clock.instant();
    for (ForensicExportJobEntity export : exportRepository
        .findTop100ByExpiresAtBeforeAndStatusNot(now, "EXPIRED")) {
      if (delete(export.getStorageUri())) exportRepository.markExpired(export.getId());
    }
    for (ForensicReplayRunEntity replay : replayRepository
        .findTop100ByExpiresAtBeforeAndStatusNot(now, "EXPIRED")) {
      if (delete(replay.getResultUri())) replayRepository.markExpired(replay.getId());
    }
    for (ForensicTwinForkEntity fork : forkRepository
        .findTop100ByExpiresAtBeforeAndStatus(now, "READY")) {
      if (delete(fork.getSnapshotUri())) forkRepository.markExpired(fork.getId());
    }
    expireEphemeralRows(now);
  }

  private void expireEphemeralRows(Instant now) {
    graphRepository.deleteExpired(now);
    sessionRepository.deleteAll(sessionRepository.findTop100ByExpiresAtBefore(now));
  }

  private boolean delete(String uri) {
    if (uri == null || uri.isBlank()) return true;
    try { storage.delete(uri); return true; }
    catch (Exception exception) {
      log.warn("Cannot delete expired forensic artifact {}: {}", uri, exception.toString());
      return false;
    }
  }
}
