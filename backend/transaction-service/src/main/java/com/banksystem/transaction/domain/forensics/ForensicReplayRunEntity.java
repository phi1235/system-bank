package com.banksystem.transaction.domain.forensics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "forensic_replay_runs")
public class ForensicReplayRunEntity {
  @Id private UUID id;
  @Column(name = "fork_id", nullable = false) private UUID forkId;
  @Column(name = "requested_by", nullable = false) private UUID requestedBy;
  @Column(name = "idempotency_key", nullable = false, length = 160) private String idempotencyKey;
  @Column(name = "request_fingerprint", nullable = false, length = 64) private String requestFingerprint;
  @Column(name = "scenario_id", nullable = false, length = 100) private String scenarioId;
  @Column(nullable = false) private long seed;
  @Column(name = "target_commit_sha", nullable = false, length = 64) private String targetCommitSha;
  @Column(nullable = false, length = 20) private String status;
  @Column(name = "result_uri", length = 500) private String resultUri;
  @Column(name = "result_sha256", length = 64) private String resultSha256;
  @Column(name = "error_detail", length = 500) private String errorDetail;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "started_at") private Instant startedAt;
  @Column(name = "completed_at") private Instant completedAt;
  @Column(name = "expires_at", nullable = false) private Instant expiresAt;

  public static ForensicReplayRunEntity pending(
      UUID id, UUID forkId, UUID actor, String key, String fingerprint, String scenarioId,
      long seed, String commitSha, Instant now, Instant expiresAt) {
    ForensicReplayRunEntity entity = new ForensicReplayRunEntity();
    entity.id = id;
    entity.forkId = forkId;
    entity.requestedBy = actor;
    entity.idempotencyKey = key;
    entity.requestFingerprint = fingerprint;
    entity.scenarioId = scenarioId;
    entity.seed = seed;
    entity.targetCommitSha = commitSha;
    entity.status = "PENDING";
    entity.createdAt = now;
    entity.expiresAt = expiresAt;
    return entity;
  }

  public void running(Instant now) { status = "RUNNING"; startedAt = now; }
  public void finish(boolean passed, String uri, String checksum, Instant now) {
    status = passed ? "PASSED" : "FAILED";
    resultUri = uri;
    resultSha256 = checksum;
    completedAt = now;
  }
  public void error(String detail, Instant now) {
    status = "ERROR";
    errorDetail = detail;
    completedAt = now;
  }

  public UUID getId() { return id; }
  public UUID getForkId() { return forkId; }
  public UUID getRequestedBy() { return requestedBy; }
  public String getIdempotencyKey() { return idempotencyKey; }
  public String getRequestFingerprint() { return requestFingerprint; }
  public String getScenarioId() { return scenarioId; }
  public long getSeed() { return seed; }
  public String getTargetCommitSha() { return targetCommitSha; }
  public String getStatus() { return status; }
  public String getResultUri() { return resultUri; }
  public String getResultSha256() { return resultSha256; }
  public String getErrorDetail() { return errorDetail; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getStartedAt() { return startedAt; }
  public Instant getCompletedAt() { return completedAt; }
  public Instant getExpiresAt() { return expiresAt; }
}
