package com.banksystem.transaction.domain.risk;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "risk_rules")
public class RiskRuleEntity {
  @Id private UUID id;
  @Column(nullable = false, unique = true, length = 80) private String code;
  @Column(name = "rule_type", nullable = false, length = 30) private String ruleType;
  @Column(nullable = false, length = 20) private String action;
  @Column(nullable = false) private boolean enabled;
  @Column(nullable = false) private int priority;
  @Column(name = "threshold_amount", precision = 19, scale = 2) private BigDecimal thresholdAmount;
  @Column(name = "window_seconds") private Long windowSeconds;
  @Column(name = "max_count") private Long maxCount;
  @Column(name = "max_total_amount", precision = 19, scale = 2) private BigDecimal maxTotalAmount;
  @Column(length = 255) private String description;
  @Version @Column(nullable = false) private long version;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "updated_at", nullable = false) private Instant updatedAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getCode() { return code; }
  public void setCode(String code) { this.code = code; }
  public String getRuleType() { return ruleType; }
  public void setRuleType(String ruleType) { this.ruleType = ruleType; }
  public String getAction() { return action; }
  public void setAction(String action) { this.action = action; }
  public boolean isEnabled() { return enabled; }
  public void setEnabled(boolean enabled) { this.enabled = enabled; }
  public int getPriority() { return priority; }
  public void setPriority(int priority) { this.priority = priority; }
  public BigDecimal getThresholdAmount() { return thresholdAmount; }
  public void setThresholdAmount(BigDecimal value) { this.thresholdAmount = value; }
  public Long getWindowSeconds() { return windowSeconds; }
  public void setWindowSeconds(Long value) { this.windowSeconds = value; }
  public Long getMaxCount() { return maxCount; }
  public void setMaxCount(Long value) { this.maxCount = value; }
  public BigDecimal getMaxTotalAmount() { return maxTotalAmount; }
  public void setMaxTotalAmount(BigDecimal value) { this.maxTotalAmount = value; }
  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
