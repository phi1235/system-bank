package com.banksystem.transaction.domain.forensics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "forensic_case_history")
public class ForensicCaseHistoryEntity {
  @Id private UUID id;
  @Column(name = "case_id", nullable = false) private UUID caseId;
  @Column(name = "actor_user_id", nullable = false) private UUID actorUserId;
  @Column(nullable = false, length = 50) private String action;
  @Column(name = "from_status", length = 30) private String fromStatus;
  @Column(name = "to_status", nullable = false, length = 30) private String toStatus;
  @Column(length = 40) private String decision;
  @Column(length = 2000) private String note;
  @Column(name = "case_version", nullable = false) private long caseVersion;
  @Column(name = "created_at", nullable = false) private Instant createdAt;

  public static ForensicCaseHistoryEntity of(
      UUID caseId, UUID actor, String action, String fromStatus, String toStatus,
      String decision, String note, long caseVersion, Instant now) {
    ForensicCaseHistoryEntity entity = new ForensicCaseHistoryEntity();
    entity.id = UUID.randomUUID();
    entity.caseId = caseId;
    entity.actorUserId = actor;
    entity.action = action;
    entity.fromStatus = fromStatus;
    entity.toStatus = toStatus;
    entity.decision = decision;
    entity.note = note;
    entity.caseVersion = caseVersion;
    entity.createdAt = now;
    return entity;
  }

  public UUID getId() { return id; }
  public UUID getCaseId() { return caseId; }
  public UUID getActorUserId() { return actorUserId; }
  public String getAction() { return action; }
  public String getFromStatus() { return fromStatus; }
  public String getToStatus() { return toStatus; }
  public String getDecision() { return decision; }
  public String getNote() { return note; }
  public long getCaseVersion() { return caseVersion; }
  public Instant getCreatedAt() { return createdAt; }
}
