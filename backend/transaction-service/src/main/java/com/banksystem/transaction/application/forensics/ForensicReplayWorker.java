package com.banksystem.transaction.application.forensics;

import com.banksystem.transaction.domain.forensics.ForensicReplayRunEntity;
import com.banksystem.transaction.domain.forensics.ForensicReplayRunRepository;
import com.banksystem.transaction.domain.forensics.ForensicTwinForkEntity;
import com.banksystem.transaction.domain.forensics.ForensicTwinForkRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class ForensicReplayWorker {
  private final ForensicReplayRunRepository runRepository;
  private final ForensicTwinForkRepository forkRepository;
  private final ForensicArtifactStorage storage;
  private final ForensicArtifactCodec codec;
  private final Clock clock;
  private final ForensicReplayExecutor executor;

  public ForensicReplayWorker(
      ForensicReplayRunRepository runRepository,
      ForensicTwinForkRepository forkRepository,
      ForensicArtifactStorage storage,
      ForensicArtifactCodec codec,
      Clock clock,
      ForensicReplayExecutor executor) {
    this.runRepository = runRepository;
    this.forkRepository = forkRepository;
    this.storage = storage;
    this.codec = codec;
    this.clock = clock;
    this.executor = executor;
  }

  @Async
  public void execute(UUID runId) {
    ForensicReplayRunEntity run = runRepository.findById(runId).orElse(null);
    if (run == null || !"PENDING".equals(run.getStatus())) return;
    run.running(clock.instant());
    runRepository.save(run);
    try {
      ForensicTwinForkEntity fork = forkRepository.findById(run.getForkId()).orElseThrow();
      byte[] snapshot = storage.get(fork.getSnapshotUri()).content();
      if (!codec.sha256(snapshot).equals(fork.getSnapshotSha256())) {
        throw new IllegalStateException("SNAPSHOT_CHECKSUM_INVALID");
      }
      JsonNode evidence = codec.read(snapshot);
      ForensicReplayExecutor.ReplayExecution execution = executor.execute(
          evidence, run.getScenarioId(), run.getSeed(), run.getTargetCommitSha());
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("schemaVersion", 1);
      result.put("runId", runId);
      result.put("scenarioId", run.getScenarioId());
      result.put("seed", run.getSeed());
      result.put("targetCommitSha", run.getTargetCommitSha());
      result.put("snapshotSha256", fork.getSnapshotSha256());
      result.put("executionMode", "SANITIZED_DETERMINISTIC_VERIFICATION");
      result.put("executionImageSha", execution.executionImageSha());
      result.put("productionEgress", false);
      result.put("beforeViolations", execution.beforeViolations());
      result.put("afterViolations", execution.afterViolations());
      result.put("resolvedViolations", execution.resolvedViolations());
      result.put("newViolations", execution.newViolations());
      result.put("passed", execution.passed());
      result.put("completedAt", clock.instant());
      byte[] content = codec.write(result);
      String checksum = codec.sha256(content);
      String uri = storage.put("replays/" + runId + "/result.json", content, "application/json");
      run.finish(execution.passed(), uri, checksum, clock.instant());
    } catch (RuntimeException exception) {
      String detail = exception.getMessage() == null
          ? exception.getClass().getSimpleName() : exception.getMessage();
      run.error(detail.substring(0, Math.min(detail.length(), 500)), clock.instant());
    }
    runRepository.save(run);
  }
}
