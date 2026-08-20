package com.banksystem.corporate.domain.payout;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payout_items")
public class PayoutItemEntity {

  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "batch_id", nullable = false)
  private PayoutBatchEntity batch;

  @Column(name = "batch_id", insertable = false, updatable = false)
  private UUID batchId;

  @Column(name = "row_number", nullable = false)
  private int rowNumber;

  @Column(name = "employee_code", length = 100)
  private String employeeCode;

  @Column(name = "beneficiary_name", nullable = false, length = 160)
  private String beneficiaryName;

  @Column(name = "account_number", nullable = false, length = 30)
  private String accountNumber;

  @Column(name = "bank_code", nullable = false, length = 32)
  private String bankCode;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Column(name = "fee_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal feeAmount = BigDecimal.ZERO;

  @Column(nullable = false, length = 3)
  private String currency = "VND";

  @Column(length = 255)
  private String description;

  @Column(name = "employee_email", length = 160)
  private String employeeEmail;

  @Column(name = "payroll_period", length = 50)
  private String payrollPeriod;

  @Column(nullable = false, length = 30)
  private String status = "IMPORTED";

  @Column(name = "validation_error", length = 500)
  private String validationError;

  @Column(name = "transaction_id")
  private UUID transactionId;

  @Column(name = "idempotency_key", length = 100, unique = true)
  private String idempotencyKey;

  @Column(name = "execution_version", nullable = false)
  private int executionVersion = 1;

  @Column(name = "retry_count", nullable = false)
  private int retryCount = 0;

  @Column(name = "next_retry_at")
  private Instant nextRetryAt;

  @Column(name = "claimed_by", length = 100)
  private String claimedBy;

  @Column(name = "claimed_at")
  private Instant claimedAt;

  @Column(name = "lease_until")
  private Instant leaseUntil;

  @Column(name = "processed_at")
  private Instant processedAt;

  @Column(name = "failure_reason", length = 500)
  private String failureReason;

  @Column(name = "receipt_artifact_id")
  private UUID receiptArtifactId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @Version
  @Column(nullable = false)
  private long version;

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
  public int getRowNumber() { return rowNumber; }
  public void setRowNumber(int rowNumber) { this.rowNumber = rowNumber; }
  public String getEmployeeCode() { return employeeCode; }
  public void setEmployeeCode(String employeeCode) { this.employeeCode = employeeCode; }
  public String getBeneficiaryName() { return beneficiaryName; }
  public void setBeneficiaryName(String beneficiaryName) { this.beneficiaryName = beneficiaryName; }
  public String getAccountNumber() { return accountNumber; }
  public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
  public String getBankCode() { return bankCode; }
  public void setBankCode(String bankCode) { this.bankCode = bankCode; }
  public BigDecimal getAmount() { return amount; }
  public void setAmount(BigDecimal amount) { this.amount = amount; }
  public BigDecimal getFeeAmount() { return feeAmount; }
  public void setFeeAmount(BigDecimal feeAmount) { this.feeAmount = feeAmount; }
  public String getCurrency() { return currency; }
  public void setCurrency(String currency) { this.currency = currency; }
  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }
  public String getEmployeeEmail() { return employeeEmail; }
  public void setEmployeeEmail(String employeeEmail) { this.employeeEmail = employeeEmail; }
  public String getPayrollPeriod() { return payrollPeriod; }
  public void setPayrollPeriod(String payrollPeriod) { this.payrollPeriod = payrollPeriod; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getValidationError() { return validationError; }
  public void setValidationError(String validationError) { this.validationError = validationError; }
  public UUID getTransactionId() { return transactionId; }
  public void setTransactionId(UUID transactionId) { this.transactionId = transactionId; }
  public String getIdempotencyKey() { return idempotencyKey; }
  public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
  public int getExecutionVersion() { return executionVersion; }
  public void setExecutionVersion(int executionVersion) { this.executionVersion = executionVersion; }
  public int getRetryCount() { return retryCount; }
  public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
  public Instant getNextRetryAt() { return nextRetryAt; }
  public void setNextRetryAt(Instant nextRetryAt) { this.nextRetryAt = nextRetryAt; }
  public String getClaimedBy() { return claimedBy; }
  public void setClaimedBy(String claimedBy) { this.claimedBy = claimedBy; }
  public Instant getClaimedAt() { return claimedAt; }
  public void setClaimedAt(Instant claimedAt) { this.claimedAt = claimedAt; }
  public Instant getLeaseUntil() { return leaseUntil; }
  public void setLeaseUntil(Instant leaseUntil) { this.leaseUntil = leaseUntil; }
  public Instant getProcessedAt() { return processedAt; }
  public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
  public String getFailureReason() { return failureReason; }
  public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
  public UUID getReceiptArtifactId() { return receiptArtifactId; }
  public void setReceiptArtifactId(UUID receiptArtifactId) { this.receiptArtifactId = receiptArtifactId; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
  public long getVersion() { return version; }
  public void setVersion(long version) { this.version = version; }
}
