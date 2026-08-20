package com.banksystem.corporate.domain.approval;

import com.banksystem.corporate.domain.payout.PayoutBatchEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "approval_instances")
public class ApprovalInstanceEntity {

  @Id
  private UUID id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "batch_id", nullable = false, unique = true)
  private PayoutBatchEntity batch;

  @Column(name = "batch_id", insertable = false, updatable = false)
  private UUID batchId;

  @Column(name = "tier_id")
  private UUID tierId;

  @Column(name = "policy_version", nullable = false)
  private int policyVersion;

  @Column(name = "total_steps", nullable = false)
  private int totalSteps;

  @Column(name = "current_step", nullable = false)
  private int currentStep = 1;

  @Column(nullable = false, length = 30)
  private String status = "IN_PROGRESS"; // IN_PROGRESS, APPROVED, REJECTED, RETURNED, CANCELLED

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @Version
  @Column(nullable = false)
  private long version;

  @OneToMany(mappedBy = "instance", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
  @OrderBy("stepOrder ASC")
  private List<ApprovalTaskEntity> tasks = new ArrayList<>();

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public PayoutBatchEntity getBatch() { return batch; }
  public void setBatch(PayoutBatchEntity batch) {
    this.batch = batch;
    if (batch != null) {
      this.batchId = batch.getId();
    }
  }
  public UUID getBatchId() { return batchId; }
  public void setBatchId(UUID batchId) { this.batchId = batchId; }
  public UUID getTierId() { return tierId; }
  public void setTierId(UUID tierId) { this.tierId = tierId; }
  public int getPolicyVersion() { return policyVersion; }
  public void setPolicyVersion(int policyVersion) { this.policyVersion = policyVersion; }
  public int getTotalSteps() { return totalSteps; }
  public void setTotalSteps(int totalSteps) { this.totalSteps = totalSteps; }
  public int getCurrentStep() { return currentStep; }
  public void setCurrentStep(int currentStep) { this.currentStep = currentStep; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
  public long getVersion() { return version; }
  public void setVersion(long version) { this.version = version; }
  public List<ApprovalTaskEntity> getTasks() { return tasks; }
  public void setTasks(List<ApprovalTaskEntity> tasks) { this.tasks = tasks; }
}
