package com.banksystem.transaction.domain.forensics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "forensic_verification_runs")
public class ForensicVerificationRunEntity {
  @Id private UUID id;
  @Column(name = "transaction_id", nullable = false) private UUID transactionId;
  @Column(name = "requested_by", nullable = false) private UUID requestedBy;
  @Column(name = "idempotency_key", nullable = false, length = 120) private String idempotencyKey;
  @Column(name = "rule_set_version", nullable = false, length = 40) private String ruleSetVersion;
  @Column(nullable = false, length = 20) private String status;
  @Column(length = 20) private String outcome;
  @Column(name = "source_watermark") private Instant sourceWatermark;
  @Column(name = "started_at", nullable = false) private Instant startedAt;
  @Column(name = "completed_at") private Instant completedAt;

  public static ForensicVerificationRunEntity running(
      UUID id, UUID transactionId, UUID actor, String idempotencyKey, Instant now) {
    ForensicVerificationRunEntity entity = new ForensicVerificationRunEntity();
    entity.id = id;
    entity.transactionId = transactionId;
    entity.requestedBy = actor;
    entity.idempotencyKey = idempotencyKey;
    entity.ruleSetVersion = "v1";
    entity.status = "RUNNING";
    entity.startedAt = now;
    return entity;
  }

  public void complete(String outcome, Instant sourceWatermark, Instant now) {
    this.status = "COMPLETED";
    this.outcome = outcome;
    this.sourceWatermark = sourceWatermark;
    this.completedAt = now;
  }

  public UUID getId() { return id; }
  public UUID getTransactionId() { return transactionId; }
  public String getRuleSetVersion() { return ruleSetVersion; }
  public String getStatus() { return status; }
  public String getOutcome() { return outcome; }
  public Instant getSourceWatermark() { return sourceWatermark; }
  public Instant getStartedAt() { return startedAt; }
  public Instant getCompletedAt() { return completedAt; }
}
