package com.banksystem.corporate.domain.approval;

import com.banksystem.corporate.domain.payout.PayoutBatchEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "approval_tasks")
public class ApprovalTaskEntity {

  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "instance_id", nullable = false)
  private ApprovalInstanceEntity instance;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "batch_id", nullable = false)
  private PayoutBatchEntity batch;

  @Column(name = "batch_id", insertable = false, updatable = false)
  private UUID batchId;

  @Column(name = "step_order", nullable = false)
  private int stepOrder;

  @Column(name = "step_name", nullable = false, length = 100)
  private String stepName;

  @Column(name = "required_role", nullable = false, length = 50)
  private String requiredRole;

  @Column(name = "min_approvals", nullable = false)
  private int minApprovals = 1;

  @Column(name = "current_approvals", nullable = false)
  private int currentApprovals = 0;

  @Column(name = "auth_method", nullable = false, length = 30)
  private String authMethod = "STANDARD"; // STANDARD, TOTP_STEPUP, DIGITAL_SIGNATURE_CA

  @Column(nullable = false, length = 30)
  private String status = "PENDING"; // PENDING, ACTIVE, APPROVED, REJECTED, RETURNED, SKIPPED

  @Column(name = "deadline")
  private Instant deadline;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @Version
  @Column(nullable = false)
  private long version;

  @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
  private List<ApprovalActionEntity> actions = new ArrayList<>();

  public boolean isSatisfied() {
    return currentApprovals >= minApprovals;
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public ApprovalInstanceEntity getInstance() { return instance; }
  public void setInstance(ApprovalInstanceEntity instance) { this.instance = instance; }
  public PayoutBatchEntity getBatch() { return batch; }
  public void setBatch(PayoutBatchEntity batch) {
    this.batch = batch;
    if (batch != null) {
      this.batchId = batch.getId();
    }
  }
  public UUID getBatchId() { return batchId; }
  public void setBatchId(UUID batchId) { this.batchId = batchId; }
  public int getStepOrder() { return stepOrder; }
  public void setStepOrder(int stepOrder) { this.stepOrder = stepOrder; }
  public String getStepName() { return stepName; }
  public void setStepName(String stepName) { this.stepName = stepName; }
  public String getRequiredRole() { return requiredRole; }
  public void setRequiredRole(String requiredRole) { this.requiredRole = requiredRole; }
  public int getMinApprovals() { return minApprovals; }
  public void setMinApprovals(int minApprovals) { this.minApprovals = minApprovals; }
  public int getCurrentApprovals() { return currentApprovals; }
  public void setCurrentApprovals(int currentApprovals) { this.currentApprovals = currentApprovals; }
  public String getAuthMethod() { return authMethod; }
  public void setAuthMethod(String authMethod) { this.authMethod = authMethod; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getDeadline() { return deadline; }
  public void setDeadline(Instant deadline) { this.deadline = deadline; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
  public long getVersion() { return version; }
  public void setVersion(long version) { this.version = version; }
  public List<ApprovalActionEntity> getActions() { return actions; }
  public void setActions(List<ApprovalActionEntity> actions) { this.actions = actions; }
}
