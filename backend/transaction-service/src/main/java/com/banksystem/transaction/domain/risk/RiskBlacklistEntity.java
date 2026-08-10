package com.banksystem.transaction.domain.risk;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "risk_blacklist")
public class RiskBlacklistEntity {
  @Id private UUID id;
  @Column(name = "subject_type", nullable = false, length = 30) private String subjectType;
  @Column(name = "subject_value", nullable = false, length = 160) private String subjectValue;
  @Column(nullable = false, length = 500) private String reason;
  @Column(nullable = false) private boolean active;
  @Column(name = "expires_at") private Instant expiresAt;
  @Column(name = "created_by", nullable = false) private UUID createdBy;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "updated_at", nullable = false) private Instant updatedAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getSubjectType() { return subjectType; }
  public void setSubjectType(String value) { this.subjectType = value; }
  public String getSubjectValue() { return subjectValue; }
  public void setSubjectValue(String value) { this.subjectValue = value; }
  public String getReason() { return reason; }
  public void setReason(String reason) { this.reason = reason; }
  public boolean isActive() { return active; }
  public void setActive(boolean active) { this.active = active; }
  public Instant getExpiresAt() { return expiresAt; }
  public void setExpiresAt(Instant value) { this.expiresAt = value; }
  public UUID getCreatedBy() { return createdBy; }
  public void setCreatedBy(UUID value) { this.createdBy = value; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant value) { this.createdAt = value; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant value) { this.updatedAt = value; }
}
