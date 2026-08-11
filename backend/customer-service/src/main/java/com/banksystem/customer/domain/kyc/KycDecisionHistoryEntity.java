package com.banksystem.customer.domain.kyc;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "kyc_decision_history")
public class KycDecisionHistoryEntity {
  @Id private UUID id;
  @Column(name = "case_id", nullable = false) private UUID caseId;
  @Column(name = "actor_id", nullable = false) private UUID actorId;
  @Column(nullable = false, length = 40) private String action;
  @Column(name = "from_status", length = 30) private String fromStatus;
  @Column(name = "to_status", nullable = false, length = 30) private String toStatus;
  @Column(length = 500) private String note;
  @Column(name = "created_at", nullable = false) private Instant createdAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public UUID getCaseId() { return caseId; }
  public void setCaseId(UUID caseId) { this.caseId = caseId; }
  public UUID getActorId() { return actorId; }
  public void setActorId(UUID actorId) { this.actorId = actorId; }
  public String getAction() { return action; }
  public void setAction(String action) { this.action = action; }
  public String getFromStatus() { return fromStatus; }
  public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }
  public String getToStatus() { return toStatus; }
  public void setToStatus(String toStatus) { this.toStatus = toStatus; }
  public String getNote() { return note; }
  public void setNote(String note) { this.note = note; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
