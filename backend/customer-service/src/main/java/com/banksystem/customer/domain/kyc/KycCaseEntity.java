package com.banksystem.customer.domain.kyc;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "kyc_cases")
public class KycCaseEntity {
  @Id private UUID id;
  @Column(name = "customer_id", nullable = false) private UUID customerId;
  @Column(name = "is_current", nullable = false) private boolean current = true;
  @Column(nullable = false, length = 30) private String status;
  @Column(name = "maker_id") private UUID makerId;
  @Column(name = "maker_recommendation", length = 20) private String makerRecommendation;
  @Column(name = "maker_note", length = 500) private String makerNote;
  @Column(name = "maker_at") private Instant makerAt;
  @Column(name = "checker_id") private UUID checkerId;
  @Column(length = 20) private String decision;
  @Column(name = "decision_reason", length = 500) private String decisionReason;
  @Column(name = "submitted_at") private Instant submittedAt;
  @Column(name = "decided_at") private Instant decidedAt;
  @Version @Column(nullable = false) private long version;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "updated_at", nullable = false) private Instant updatedAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public UUID getCustomerId() { return customerId; }
  public void setCustomerId(UUID customerId) { this.customerId = customerId; }
  public boolean isCurrent() { return current; }
  public void setCurrent(boolean current) { this.current = current; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public UUID getMakerId() { return makerId; }
  public void setMakerId(UUID makerId) { this.makerId = makerId; }
  public String getMakerRecommendation() { return makerRecommendation; }
  public void setMakerRecommendation(String value) { this.makerRecommendation = value; }
  public String getMakerNote() { return makerNote; }
  public void setMakerNote(String makerNote) { this.makerNote = makerNote; }
  public Instant getMakerAt() { return makerAt; }
  public void setMakerAt(Instant makerAt) { this.makerAt = makerAt; }
  public UUID getCheckerId() { return checkerId; }
  public void setCheckerId(UUID checkerId) { this.checkerId = checkerId; }
  public String getDecision() { return decision; }
  public void setDecision(String decision) { this.decision = decision; }
  public String getDecisionReason() { return decisionReason; }
  public void setDecisionReason(String decisionReason) { this.decisionReason = decisionReason; }
  public Instant getSubmittedAt() { return submittedAt; }
  public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
  public Instant getDecidedAt() { return decidedAt; }
  public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
