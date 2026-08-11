package com.banksystem.transaction.domain.risk;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "risk_assessments")
public class RiskAssessmentEntity {
  @Id private UUID id;
  @Column(name = "transfer_id", nullable = false, unique = true) private UUID transferId;
  @Column(name = "user_id", nullable = false) private UUID userId;
  @Column(nullable = false, length = 20) private String decision;
  @Column(nullable = false) private int score;
  @Column(name = "matched_rules", columnDefinition = "TEXT") private String matchedRules;
  @Column(length = 500) private String reason;
  @Column(name = "created_at", nullable = false) private Instant createdAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public UUID getTransferId() { return transferId; }
  public void setTransferId(UUID value) { this.transferId = value; }
  public UUID getUserId() { return userId; }
  public void setUserId(UUID value) { this.userId = value; }
  public String getDecision() { return decision; }
  public void setDecision(String value) { this.decision = value; }
  public int getScore() { return score; }
  public void setScore(int value) { this.score = value; }
  public String getMatchedRules() { return matchedRules; }
  public void setMatchedRules(String value) { this.matchedRules = value; }
  public String getReason() { return reason; }
  public void setReason(String value) { this.reason = value; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant value) { this.createdAt = value; }
}
