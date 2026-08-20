package com.banksystem.corporate.domain.approval;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "approval_tiers")
public class ApprovalTierEntity {

  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "policy_id", nullable = false)
  private ApprovalPolicyEntity policy;

  @Column(name = "tier_name", nullable = false, length = 100)
  private String tierName;

  @Column(name = "min_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal minAmount = BigDecimal.ZERO;

  @Column(name = "max_amount", precision = 19, scale = 2)
  private BigDecimal maxAmount;

  @Column(name = "priority_order", nullable = false)
  private int priorityOrder = 1;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @OneToMany(mappedBy = "tier", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
  @OrderBy("stepOrder ASC")
  private List<ApprovalStepTemplateEntity> steps = new ArrayList<>();

  public boolean matchesAmount(BigDecimal amount) {
    if (amount == null) return false;
    if (minAmount != null && amount.compareTo(minAmount) < 0) return false;
    if (maxAmount != null && amount.compareTo(maxAmount) >= 0) return false;
    return true;
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public ApprovalPolicyEntity getPolicy() { return policy; }
  public void setPolicy(ApprovalPolicyEntity policy) { this.policy = policy; }
  public String getTierName() { return tierName; }
  public void setTierName(String tierName) { this.tierName = tierName; }
  public BigDecimal getMinAmount() { return minAmount; }
  public void setMinAmount(BigDecimal minAmount) { this.minAmount = minAmount; }
  public BigDecimal getMaxAmount() { return maxAmount; }
  public void setMaxAmount(BigDecimal maxAmount) { this.maxAmount = maxAmount; }
  public int getPriorityOrder() { return priorityOrder; }
  public void setPriorityOrder(int priorityOrder) { this.priorityOrder = priorityOrder; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public List<ApprovalStepTemplateEntity> getSteps() { return steps; }
  public void setSteps(List<ApprovalStepTemplateEntity> steps) { this.steps = steps; }
}
