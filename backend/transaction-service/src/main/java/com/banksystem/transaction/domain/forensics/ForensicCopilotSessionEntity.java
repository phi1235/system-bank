package com.banksystem.transaction.domain.forensics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "forensic_copilot_sessions")
public class ForensicCopilotSessionEntity {
  @Id private UUID id;
  @Column(name = "transaction_id") private UUID transactionId;
  @Column(name = "case_id") private UUID caseId;
  @Column(name = "created_by", nullable = false) private UUID createdBy;
  @Column(nullable = false, length = 20) private String status;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "updated_at", nullable = false) private Instant updatedAt;
  @Column(name = "expires_at", nullable = false) private Instant expiresAt;

  public static ForensicCopilotSessionEntity active(
      UUID id, UUID transactionId, UUID caseId, UUID actor, Instant now, Instant expiresAt) {
    ForensicCopilotSessionEntity entity = new ForensicCopilotSessionEntity();
    entity.id = id;
    entity.transactionId = transactionId;
    entity.caseId = caseId;
    entity.createdBy = actor;
    entity.status = "ACTIVE";
    entity.createdAt = now;
    entity.updatedAt = now;
    entity.expiresAt = expiresAt;
    return entity;
  }

  public void touch(Instant now) { updatedAt = now; }
  public UUID getId() { return id; }
  public UUID getTransactionId() { return transactionId; }
  public UUID getCaseId() { return caseId; }
  public UUID getCreatedBy() { return createdBy; }
  public String getStatus() { return status; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public Instant getExpiresAt() { return expiresAt; }
}
