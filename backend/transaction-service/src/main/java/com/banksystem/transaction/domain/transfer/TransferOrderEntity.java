package com.banksystem.transaction.domain.transfer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transfer_orders")
public class TransferOrderEntity {

  @Id
  private UUID id;

  @Version
  @Column(nullable = false)
  private long version;

  @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
  private String idempotencyKey;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "corporate_id")
  private UUID corporateId;

  @Column(name = "batch_id")
  private UUID batchId;

  @Column(name = "batch_item_id")
  private UUID batchItemId;

  @Column(name = "hold_id")
  private UUID holdId;

  @Column(name = "initiated_by")
  private UUID initiatedBy;

  @Column(name = "execution_version", nullable = false)
  private int executionVersion = 1;

  @Column(name = "from_account_id", nullable = false)
  private UUID fromAccountId;

  @Column(name = "to_account_id")
  private UUID toAccountId;

  @Column(name = "to_account_number", nullable = false, length = 20)
  private String toAccountNumber;

  @Column(name = "transfer_type", nullable = false, length = 20)
  private String transferType = "INTERNAL";

  @Column(name = "target_bank_code", length = 32)
  private String targetBankCode;

  @Column(name = "target_account_name", length = 160)
  private String targetAccountName;

  @Column(name = "beneficiary_inquiry_id", length = 64, unique = true)
  private String beneficiaryInquiryId;

  @Column(name = "provider_reference_id", length = 100)
  private String providerReferenceId;

  @Column(name = "provider_status", length = 30)
  private String providerStatus;

  @Column(name = "provider_attempt_count", nullable = false)
  private int providerAttemptCount;

  @Column(name = "last_provider_query_at")
  private Instant lastProviderQueryAt;

  @Column(name = "risk_decision", length = 20)
  private String riskDecision;

  @Column(name = "risk_score")
  private Integer riskScore;

  @Column(name = "risk_reason", length = 500)
  private String riskReason;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  /** Fee charged on source (in addition to amount); destination receives amount only. */
  @Column(name = "fee_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal feeAmount = BigDecimal.ZERO;

  @Column(nullable = false, length = 3)
  private String currency = "VND";

  @Column(length = 255)
  private String description;

  @Column(name = "request_fingerprint", nullable = false, length = 128)
  private String requestFingerprint;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TransferStatus status = TransferStatus.PENDING;

  @Column(name = "failure_reason", length = 255)
  private String failureReason;

  @Column(name = "debit_entry_ref", length = 64)
  private String debitEntryRef;

  @Column(name = "credit_entry_ref", length = 64)
  private String creditEntryRef;

  @Column(name = "fee_entry_ref", length = 64)
  private String feeEntryRef;

  @Column(name = "inquiry_id", length = 64)
  private String inquiryId;

  @Column(name = "bank_bin", length = 20)
  private String bankBin;

  @Column(name = "recipient_name", length = 255)
  private String recipientName;

  @Column(name = "total_debit", precision = 19, scale = 2)
  private BigDecimal totalDebit;

  @Column(name = "napas_rrn", length = 50)
  private String napasRrn;

  @Column(name = "napas_trace_no", length = 50)
  private String napasTraceNo;

  @Column(name = "reconciliation_attempts", nullable = false)
  private int reconciliationAttempts = 0;

  @Column(name = "next_reconciliation_at")
  private Instant nextReconciliationAt;

  @Column(name = "reconciliation_status", length = 32)
  private String reconciliationStatus;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public long getVersion() {
    return version;
  }

  public void setVersion(long version) {
    this.version = version;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public void setIdempotencyKey(String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public UUID getFromAccountId() {
    return fromAccountId;
  }

  public void setFromAccountId(UUID fromAccountId) {
    this.fromAccountId = fromAccountId;
  }

  public UUID getToAccountId() {
    return toAccountId;
  }

  public void setToAccountId(UUID toAccountId) {
    this.toAccountId = toAccountId;
  }

  public String getToAccountNumber() {
    return toAccountNumber;
  }

  public void setToAccountNumber(String toAccountNumber) {
    this.toAccountNumber = toAccountNumber;
  }

  public String getTransferType() {
    return transferType;
  }

  public void setTransferType(String transferType) {
    this.transferType = transferType;
  }

  public String getTargetBankCode() {
    return targetBankCode;
  }

  public void setTargetBankCode(String targetBankCode) {
    this.targetBankCode = targetBankCode;
  }

  public String getTargetAccountName() {
    return targetAccountName;
  }

  public void setTargetAccountName(String targetAccountName) {
    this.targetAccountName = targetAccountName;
  }

  public String getBeneficiaryInquiryId() {
    return beneficiaryInquiryId;
  }

  public void setBeneficiaryInquiryId(String beneficiaryInquiryId) {
    this.beneficiaryInquiryId = beneficiaryInquiryId;
  }

  public String getProviderReferenceId() {
    return providerReferenceId;
  }

  public void setProviderReferenceId(String providerReferenceId) {
    this.providerReferenceId = providerReferenceId;
  }

  public String getProviderStatus() {
    return providerStatus;
  }

  public void setProviderStatus(String providerStatus) {
    this.providerStatus = providerStatus;
  }

  public int getProviderAttemptCount() {
    return providerAttemptCount;
  }

  public void setProviderAttemptCount(int providerAttemptCount) {
    this.providerAttemptCount = providerAttemptCount;
  }

  public Instant getLastProviderQueryAt() {
    return lastProviderQueryAt;
  }

  public void setLastProviderQueryAt(Instant lastProviderQueryAt) {
    this.lastProviderQueryAt = lastProviderQueryAt;
  }

  public String getRiskDecision() {
    return riskDecision;
  }

  public void setRiskDecision(String riskDecision) {
    this.riskDecision = riskDecision;
  }

  public Integer getRiskScore() {
    return riskScore;
  }

  public void setRiskScore(Integer riskScore) {
    this.riskScore = riskScore;
  }

  public String getRiskReason() {
    return riskReason;
  }

  public void setRiskReason(String riskReason) {
    this.riskReason = riskReason;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public BigDecimal getFeeAmount() {
    return feeAmount;
  }

  public void setFeeAmount(BigDecimal feeAmount) {
    this.feeAmount = feeAmount;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getRequestFingerprint() {
    return requestFingerprint;
  }

  public void setRequestFingerprint(String requestFingerprint) {
    this.requestFingerprint = requestFingerprint;
  }

  public TransferStatus getStatus() {
    return status;
  }

  public void setStatus(TransferStatus status) {
    this.status = status;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public void setFailureReason(String failureReason) {
    this.failureReason = failureReason;
  }

  public String getDebitEntryRef() {
    return debitEntryRef;
  }

  public void setDebitEntryRef(String debitEntryRef) {
    this.debitEntryRef = debitEntryRef;
  }

  public String getCreditEntryRef() {
    return creditEntryRef;
  }

  public void setCreditEntryRef(String creditEntryRef) {
    this.creditEntryRef = creditEntryRef;
  }

  public String getFeeEntryRef() {
    return feeEntryRef;
  }

  public void setFeeEntryRef(String feeEntryRef) {
    this.feeEntryRef = feeEntryRef;
  }

  public String getInquiryId() {
    return inquiryId;
  }

  public void setInquiryId(String inquiryId) {
    this.inquiryId = inquiryId;
  }

  public String getBankBin() {
    return bankBin;
  }

  public void setBankBin(String bankBin) {
    this.bankBin = bankBin;
  }

  public String getRecipientName() {
    return recipientName;
  }

  public void setRecipientName(String recipientName) {
    this.recipientName = recipientName;
  }

  public BigDecimal getTotalDebit() {
    if (totalDebit != null) {
      return totalDebit;
    }
    if (amount == null) {
      return BigDecimal.ZERO;
    }
    BigDecimal fee = feeAmount == null ? BigDecimal.ZERO : feeAmount;
    return amount.add(fee);
  }

  public void setTotalDebit(BigDecimal totalDebit) {
    this.totalDebit = totalDebit;
  }

  public String getNapasRrn() {
    return napasRrn;
  }

  public void setNapasRrn(String napasRrn) {
    this.napasRrn = napasRrn;
  }

  public String getNapasTraceNo() {
    return napasTraceNo;
  }

  public void setNapasTraceNo(String napasTraceNo) {
    this.napasTraceNo = napasTraceNo;
  }

  public int getReconciliationAttempts() {
    return reconciliationAttempts;
  }

  public void setReconciliationAttempts(int reconciliationAttempts) {
    this.reconciliationAttempts = reconciliationAttempts;
  }

  public Instant getNextReconciliationAt() {
    return nextReconciliationAt;
  }

  public void setNextReconciliationAt(Instant nextReconciliationAt) {
    this.nextReconciliationAt = nextReconciliationAt;
  }

  public String getReconciliationStatus() {
    return reconciliationStatus;
  }

  public void setReconciliationStatus(String reconciliationStatus) {
    this.reconciliationStatus = reconciliationStatus;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public UUID getCorporateId() {
    return corporateId;
  }

  public void setCorporateId(UUID corporateId) {
    this.corporateId = corporateId;
  }

  public UUID getBatchId() {
    return batchId;
  }

  public void setBatchId(UUID batchId) {
    this.batchId = batchId;
  }

  public UUID getBatchItemId() {
    return batchItemId;
  }

  public void setBatchItemId(UUID batchItemId) {
    this.batchItemId = batchItemId;
  }

  public UUID getHoldId() {
    return holdId;
  }

  public void setHoldId(UUID holdId) {
    this.holdId = holdId;
  }

  public UUID getInitiatedBy() {
    return initiatedBy;
  }

  public void setInitiatedBy(UUID initiatedBy) {
    this.initiatedBy = initiatedBy;
  }

  public int getExecutionVersion() {
    return executionVersion;
  }

  public void setExecutionVersion(int executionVersion) {
    this.executionVersion = executionVersion;
  }
}
