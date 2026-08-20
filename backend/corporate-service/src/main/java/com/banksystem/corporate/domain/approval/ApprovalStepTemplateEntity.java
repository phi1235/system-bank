package com.banksystem.corporate.domain.approval;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "approval_step_templates")
public class ApprovalStepTemplateEntity {

  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tier_id", nullable = false)
  private ApprovalTierEntity tier;

  @Column(name = "step_order", nullable = false)
  private int stepOrder;

  @Column(name = "step_name", nullable = false, length = 100)
  private String stepName;

  @Column(name = "required_role", nullable = false, length = 50)
  private String requiredRole; // CHECKER, CFO, CHAIRMAN, etc.

  @Column(name = "min_approvals", nullable = false)
  private int minApprovals = 1;

  @Column(name = "auth_method", nullable = false, length = 30)
  private String authMethod = "STANDARD"; // STANDARD, TOTP_STEPUP, DIGITAL_SIGNATURE_CA

  @Column(name = "deadline_hours")
  private Integer deadlineHours;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public ApprovalTierEntity getTier() { return tier; }
  public void setTier(ApprovalTierEntity tier) { this.tier = tier; }
  public int getStepOrder() { return stepOrder; }
  public void setStepOrder(int stepOrder) { this.stepOrder = stepOrder; }
  public String getStepName() { return stepName; }
  public void setStepName(String stepName) { this.stepName = stepName; }
  public String getRequiredRole() { return requiredRole; }
  public void setRequiredRole(String requiredRole) { this.requiredRole = requiredRole; }
  public int getMinApprovals() { return minApprovals; }
  public void setMinApprovals(int minApprovals) { this.minApprovals = minApprovals; }
  public String getAuthMethod() { return authMethod; }
  public void setAuthMethod(String authMethod) { this.authMethod = authMethod; }
  public Integer getDeadlineHours() { return deadlineHours; }
  public void setDeadlineHours(Integer deadlineHours) { this.deadlineHours = deadlineHours; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
