package com.banksystem.transaction.application.forensics;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.common.security.GatewayUser;
import com.banksystem.common.security.SecurityHeaders;
import com.banksystem.transaction.api.dto.ForensicOperationDtos.CreateReplayRequest;
import com.banksystem.transaction.api.dto.ForensicOperationDtos.ReplayRunResponse;
import com.banksystem.transaction.api.dto.ForensicOperationDtos.TwinForkResponse;
import com.banksystem.transaction.domain.audit.AuditLogEntity;
import com.banksystem.transaction.domain.audit.AuditLogRepository;
import com.banksystem.transaction.domain.forensics.ForensicReplayRunEntity;
import com.banksystem.transaction.domain.forensics.ForensicReplayRunRepository;
import com.banksystem.transaction.domain.forensics.ForensicTwinForkEntity;
import com.banksystem.transaction.domain.forensics.ForensicTwinForkRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ForensicReplayService {
  private final ForensicTwinForkRepository forkRepository;
  private final ForensicReplayRunRepository runRepository;
  private final ForensicInvestigationQueryService investigationQueryService;
  private final ForensicEvidenceSanitizer sanitizer;
  private final ForensicArtifactCodec codec;
  private final ForensicArtifactStorage storage;
  private final ForensicReplayWorker worker;
  private final AuditLogRepository auditRepository;
  private final Clock clock;
  private final Duration retention;
  private final int quota;
  private final ForensicScenarioService scenarioService;

  public ForensicReplayService(
      ForensicTwinForkRepository forkRepository,
      ForensicReplayRunRepository runRepository,
      ForensicInvestigationQueryService investigationQueryService,
      ForensicEvidenceSanitizer sanitizer,
      ForensicArtifactCodec codec,
      ForensicArtifactStorage storage,
      ForensicReplayWorker worker,
      AuditLogRepository auditRepository,
      Clock clock,
      @Value("${bank.forensics.replay-retention}") Duration retention,
      @Value("${bank.forensics.fork-quota-per-user}") int quota,
      ForensicScenarioService scenarioService) {
    this.forkRepository = forkRepository;
    this.runRepository = runRepository;
    this.investigationQueryService = investigationQueryService;
    this.sanitizer = sanitizer;
    this.codec = codec;
    this.storage = storage;
    this.worker = worker;
    this.auditRepository = auditRepository;
    this.clock = clock;
    this.retention = retention;
    this.quota = quota;
    this.scenarioService = scenarioService;
  }

  public TwinForkResponse createFork(
      UUID transactionId, Integer ttlMinutes, GatewayUser actor) {
    Instant now = clock.instant();
    if (forkRepository.countByCreatedByAndStatusAndExpiresAtAfter(
        actor.userId(), "READY", now) >= quota) {
      throw new BusinessException("FORK_QUOTA_EXCEEDED", "Active forensic fork quota exceeded");
    }
    byte[] snapshot = codec.write(sanitizer.sanitize(investigationQueryService.get(transactionId)));
    String checksum = codec.sha256(snapshot);
    UUID id = UUID.randomUUID();
    int requestedTtl = ttlMinutes == null ? (int) retention.toMinutes() : ttlMinutes;
    Instant expiresAt = now.plus(Duration.ofMinutes(Math.min(requestedTtl, 1440)));
    String uri = storage.put("forks/" + id + "/snapshot.json", snapshot, "application/json");
    ForensicTwinForkEntity entity = ForensicTwinForkEntity.ready(
        id, transactionId, actor.userId(), uri, checksum, now, expiresAt);
    forkRepository.save(entity);
    auditRepository.save(AuditLogEntity.of(
        actor.userId(), "FORENSIC_FORK_CREATED", "FORENSIC_FORK", id.toString(), "unknown",
        "transactionId=" + transactionId + ",expiresAt=" + expiresAt));
    return toFork(entity);
  }

  @Transactional
  public ReplayRunResponse createReplay(
      String idempotencyKey, CreateReplayRequest request, GatewayUser actor) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new BusinessException("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key is required");
    }
    String normalizedKey = idempotencyKey.trim();
    String fingerprint = codec.sha256(
        request.forkId() + "|" + request.scenarioId() + "|" + request.seed() + "|"
            + request.targetCommitSha().toLowerCase());
    ForensicReplayRunEntity duplicate = runRepository
        .findByRequestedByAndIdempotencyKey(actor.userId(), normalizedKey).orElse(null);
    if (duplicate != null) {
      if (!duplicate.getRequestFingerprint().equals(fingerprint)) {
        throw new BusinessException("IDEMPOTENCY_KEY_REUSED", "Idempotency key has a different payload");
      }
      return toRun(duplicate);
    }
    ForensicTwinForkEntity fork = requireReadyFork(request.forkId());
    scenarioService.requireConfirmed(request.scenarioId().trim());
    Instant now = clock.instant();
    ForensicReplayRunEntity run = ForensicReplayRunEntity.pending(
        UUID.randomUUID(), fork.getId(), actor.userId(), normalizedKey, fingerprint,
        request.scenarioId().trim(), request.seed(), request.targetCommitSha().toLowerCase(),
        now, now.plus(retention));
    runRepository.save(run);
    auditRepository.save(AuditLogEntity.of(
        actor.userId(), "FORENSIC_REPLAY_REQUESTED", "FORENSIC_REPLAY", run.getId().toString(),
        "unknown", "forkId=" + fork.getId() + ",scenario=" + run.getScenarioId()));
    afterCommit(() -> worker.execute(run.getId()));
    return toRun(run);
  }

  @Transactional(readOnly = true)
  public ReplayRunResponse getRun(UUID id, GatewayUser actor) {
    return toRun(requireOwnedRun(id, actor));
  }

  @Transactional
  public void deleteFork(UUID id, GatewayUser actor) {
    ForensicTwinForkEntity fork = forkRepository.findById(id).orElseThrow(() ->
        new BusinessException("FORENSIC_FORK_NOT_FOUND", "Forensic fork not found"));
    if (!actor.userId().equals(fork.getCreatedBy())
        && !actor.hasPermission(SecurityHeaders.PERM_FORENSICS_ADMIN)) {
      throw new BusinessException("FORBIDDEN_FORENSICS_ACCESS", "Cannot delete another user's fork");
    }
    if ("READY".equals(fork.getStatus())) storage.delete(fork.getSnapshotUri());
    fork.delete(clock.instant());
    forkRepository.save(fork);
    auditRepository.save(AuditLogEntity.of(
        actor.userId(), "FORENSIC_FORK_DELETED", "FORENSIC_FORK", id.toString(), "unknown",
        "transactionId=" + fork.getTransactionId()));
  }

  @Transactional(readOnly = true)
  public ForensicArtifactStorage.StoredArtifact result(UUID id, GatewayUser actor) {
    ForensicReplayRunEntity run = requireOwnedRun(id, actor);
    if (!("PASSED".equals(run.getStatus()) || "FAILED".equals(run.getStatus()))) {
      throw new BusinessException("FORENSIC_REPLAY_NOT_READY", "Replay result is not available");
    }
    return storage.get(run.getResultUri());
  }

  private ForensicTwinForkEntity requireReadyFork(UUID id) {
    ForensicTwinForkEntity fork = forkRepository.findById(id).orElseThrow(() ->
        new BusinessException("FORENSIC_FORK_NOT_FOUND", "Forensic fork not found"));
    if (!"READY".equals(fork.getStatus()) || !fork.getExpiresAt().isAfter(clock.instant())) {
      throw new BusinessException("FORENSIC_FORK_EXPIRED", "Forensic fork is not available");
    }
    return fork;
  }

  private ForensicReplayRunEntity requireRun(UUID id) {
    return runRepository.findById(id).orElseThrow(() ->
        new BusinessException("FORENSIC_REPLAY_NOT_FOUND", "Forensic replay run not found"));
  }

  private ForensicReplayRunEntity requireOwnedRun(UUID id, GatewayUser actor) {
    ForensicReplayRunEntity run = requireRun(id);
    if (!run.getRequestedBy().equals(actor.userId())
        && !actor.hasPermission(SecurityHeaders.PERM_FORENSICS_ADMIN)) {
      throw new BusinessException("FORBIDDEN_FORENSICS_ACCESS", "Replay run belongs to another user");
    }
    return run;
  }

  private TwinForkResponse toFork(ForensicTwinForkEntity entity) {
    return new TwinForkResponse(
        entity.getId(), entity.getTransactionId(), entity.getStatus(), entity.getSnapshotSha256(),
        entity.getSchemaVersion(), entity.getCreatedAt(), entity.getExpiresAt());
  }

  private ReplayRunResponse toRun(ForensicReplayRunEntity entity) {
    return new ReplayRunResponse(
        entity.getId(), entity.getForkId(), entity.getScenarioId(), entity.getSeed(),
        entity.getTargetCommitSha(), entity.getStatus(), entity.getResultSha256(),
        entity.getErrorDetail(), entity.getCreatedAt(), entity.getStartedAt(),
        entity.getCompletedAt(), entity.getExpiresAt());
  }

  private void afterCommit(Runnable task) {
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override public void afterCommit() { task.run(); }
    });
  }
}
