package com.banksystem.corporate.domain.payout;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "payout_batches")
public class PayoutBatchEntity {

  @Id
  private UUID id;

  @Column(name = "corporate_id", nullable = false)
  private UUID corporateId;

  @Column(name = "source_account_id", nullable = false)
  private UUID sourceAccountId;

  @Column(name = "source_account_number", nullable = false, length = 20)
  private String sourceAccountNumber;

  @Column(name = "batch_name", nullable = false, length = 200)
  private String batchName;

  @Column(name = "total_items", nullable = false)
  private int totalItems = 0;

  @Column(name = "valid_items", nullable = false)
  private int validItems = 0;

  @Column(name = "invalid_items", nullable = false)
  private int invalidItems = 0;

  @Column(name = "processed_items", nullable = false)
  private int processedItems = 0;

  @Column(name = "successful_items", nullable = false)
  private int successfulItems = 0;

  @Column(name = "failed_items", nullable = false)
  private int failedItems = 0;

  @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal totalAmount = BigDecimal.ZERO;

  @Column(name = "total_fee", nullable = false, precision = 19, scale = 2)
  private BigDecimal totalFee = BigDecimal.ZERO;

  @Column(nullable = false, length = 3)
  private String currency = "VND";

  @Column(name = "file_sha256", nullable = false, length = 64)
  private String fileSha256;

  @Column(name = "original_file_key", length = 255)
  private String originalFileKey;

  @Column(name = "error_report_file_key", length = 255)
  private String errorReportFileKey;

  @Column(name = "policy_id")
  private UUID policyId;

  @Column(name = "policy_version")
  private Integer policyVersion;

  @Column(name = "policy_snapshot", columnDefinition = "TEXT")
  private String policySnapshot;

  @Column(name = "canonical_payload_hash", length = 64)
  private String canonicalPayloadHash;

  @Column(nullable = false, length = 30)
  private String status = "DRAFT";

  @Column(name = "hold_id")
  private UUID holdId;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "submitted_by")
  private UUID submittedBy;

  @Column(name = "submitted_at")
  private Instant submittedAt;

  @Column(name = "approved_at")
  private Instant approvedAt;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "worker_claimed_by", length = 100)
  private String workerClaimedBy;

  @Column(name = "worker_lease_until")
  private Instant workerLeaseUntil;

  @Column(name = "hold_retry_count", nullable = false)
  private int holdRetryCount;

  @Column(name = "hold_next_retry_at")
  private Instant holdNextRetryAt;

  @Column(name = "hold_last_error", length = 500)
  private String holdLastError;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @Version
  @Column(nullable = false)
  private long version;

  @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
  @OrderBy("rowNumber ASC")
  private List<PayoutItemEntity> items = new ArrayList<>();

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public UUID getCorporateId() { return corporateId; }
  public void setCorporateId(UUID corporateId) { this.corporateId = corporateId; }
  public UUID getSourceAccountId() { return sourceAccountId; }
  public void setSourceAccountId(UUID sourceAccountId) { this.sourceAccountId = sourceAccountId; }
  public String getSourceAccountNumber() { return sourceAccountNumber; }
  public void setSourceAccountNumber(String sourceAccountNumber) { this.sourceAccountNumber = sourceAccountNumber; }
  public String getBatchName() { return batchName; }
  public void setBatchName(String batchName) { this.batchName = batchName; }
  public int getTotalItems() { return totalItems; }
  public void setTotalItems(int totalItems) { this.totalItems = totalItems; }
  public int getValidItems() { return validItems; }
  public void setValidItems(int validItems) { this.validItems = validItems; }
  public int getInvalidItems() { return invalidItems; }
  public void setInvalidItems(int invalidItems) { this.invalidItems = invalidItems; }
  public int getProcessedItems() { return processedItems; }
  public void setProcessedItems(int processedItems) { this.processedItems = processedItems; }
  public int getSuccessfulItems() { return successfulItems; }
  public void setSuccessfulItems(int successfulItems) { this.successfulItems = successfulItems; }
  public int getFailedItems() { return failedItems; }
  public void setFailedItems(int failedItems) { this.failedItems = failedItems; }
  public BigDecimal getTotalAmount() { return totalAmount; }
  public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
  public BigDecimal getTotalFee() { return totalFee; }
  public void setTotalFee(BigDecimal totalFee) { this.totalFee = totalFee; }
  public String getCurrency() { return currency; }
  public void setCurrency(String currency) { this.currency = currency; }
  public String getFileSha256() { return fileSha256; }
  public void setFileSha256(String fileSha256) { this.fileSha256 = fileSha256; }
  public String getOriginalFileKey() { return originalFileKey; }
  public void setOriginalFileKey(String originalFileKey) { this.originalFileKey = originalFileKey; }
  public String getErrorReportFileKey() { return errorReportFileKey; }
  public void setErrorReportFileKey(String errorReportFileKey) { this.errorReportFileKey = errorReportFileKey; }
  public UUID getPolicyId() { return policyId; }
  public void setPolicyId(UUID policyId) { this.policyId = policyId; }
  public Integer getPolicyVersion() { return policyVersion; }
  public void setPolicyVersion(Integer policyVersion) { this.policyVersion = policyVersion; }
  public String getPolicySnapshot() { return policySnapshot; }
  public void setPolicySnapshot(String policySnapshot) { this.policySnapshot = policySnapshot; }
  public String getCanonicalPayloadHash() { return canonicalPayloadHash; }
  public void setCanonicalPayloadHash(String canonicalPayloadHash) { this.canonicalPayloadHash = canonicalPayloadHash; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public UUID getHoldId() { return holdId; }
  public void setHoldId(UUID holdId) { this.holdId = holdId; }
  public UUID getCreatedBy() { return createdBy; }
  public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
  public UUID getSubmittedBy() { return submittedBy; }
  public void setSubmittedBy(UUID submittedBy) { this.submittedBy = submittedBy; }
  public Instant getSubmittedAt() { return submittedAt; }
  public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
  public Instant getApprovedAt() { return approvedAt; }
  public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
  public Instant getStartedAt() { return startedAt; }
  public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
  public Instant getCompletedAt() { return completedAt; }
  public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
  public String getWorkerClaimedBy() { return workerClaimedBy; }
  public void setWorkerClaimedBy(String workerClaimedBy) { this.workerClaimedBy = workerClaimedBy; }
  public Instant getWorkerLeaseUntil() { return workerLeaseUntil; }
  public void setWorkerLeaseUntil(Instant workerLeaseUntil) { this.workerLeaseUntil = workerLeaseUntil; }
  public int getHoldRetryCount() { return holdRetryCount; }
  public void setHoldRetryCount(int holdRetryCount) { this.holdRetryCount = holdRetryCount; }
  public Instant getHoldNextRetryAt() { return holdNextRetryAt; }
  public void setHoldNextRetryAt(Instant holdNextRetryAt) { this.holdNextRetryAt = holdNextRetryAt; }
  public String getHoldLastError() { return holdLastError; }
  public void setHoldLastError(String holdLastError) { this.holdLastError = holdLastError; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
  public long getVersion() { return version; }
  public void setVersion(long version) { this.version = version; }
  public List<PayoutItemEntity> getItems() { return items; }
  public void setItems(List<PayoutItemEntity> items) { this.items = items; }
}
