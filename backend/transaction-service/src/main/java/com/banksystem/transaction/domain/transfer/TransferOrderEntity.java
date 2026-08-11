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

  @Column(name = "from_account_id", nullable = false)
  private UUID fromAccountId;

  @Column(name = "to_account_id")
  private UUID toAccountId;

  @Column(name = "to_account_number", nullable = false, length = 20)
  private String toAccountNumber;

  @Column(name = "transfer_type", nullable = false, length = 20)
  private String transferType = "INTERNAL";

  @Column(name = "target_bank_code", length = 20)
  private String targetBankCode;

  @Column(name = "target_account_name", length = 160)
  private String targetAccountName;

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

  /** Ledger entry id of fee CREDIT on bank income account. */
  @Column(name = "fee_entry_ref", length = 64)
  private String feeEntryRef;

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

  public String getTransferType() { return transferType; }

  public void setTransferType(String transferType) { this.transferType = transferType; }

  public String getTargetBankCode() { return targetBankCode; }

  public void setTargetBankCode(String targetBankCode) { this.targetBankCode = targetBankCode; }

  public String getTargetAccountName() { return targetAccountName; }

  public void setTargetAccountName(String targetAccountName) { this.targetAccountName = targetAccountName; }

  public String getProviderReferenceId() { return providerReferenceId; }

  public void setProviderReferenceId(String providerReferenceId) { this.providerReferenceId = providerReferenceId; }

  public String getProviderStatus() { return providerStatus; }

  public void setProviderStatus(String providerStatus) { this.providerStatus = providerStatus; }

  public int getProviderAttemptCount() { return providerAttemptCount; }

  public void setProviderAttemptCount(int providerAttemptCount) { this.providerAttemptCount = providerAttemptCount; }

  public Instant getLastProviderQueryAt() { return lastProviderQueryAt; }

  public void setLastProviderQueryAt(Instant lastProviderQueryAt) { this.lastProviderQueryAt = lastProviderQueryAt; }

  public String getRiskDecision() { return riskDecision; }
  public void setRiskDecision(String riskDecision) { this.riskDecision = riskDecision; }
  public Integer getRiskScore() { return riskScore; }
  public void setRiskScore(Integer riskScore) { this.riskScore = riskScore; }
  public String getRiskReason() { return riskReason; }
  public void setRiskReason(String riskReason) { this.riskReason = riskReason; }

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
    this.feeAmount = feeAmount == null ? BigDecimal.ZERO : feeAmount;
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
}
