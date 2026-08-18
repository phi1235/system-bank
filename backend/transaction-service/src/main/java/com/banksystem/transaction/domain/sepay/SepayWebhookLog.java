package com.banksystem.transaction.domain.sepay;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sepay_webhook_logs")
public class SepayWebhookLog {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "sepay_transaction_id", unique = true)
  private Long sepayTransactionId;

  @Column(name = "gateway", length = 64)
  private String gateway;

  @Column(name = "transaction_date", length = 64)
  private String transactionDate;

  @Column(name = "account_number", length = 32)
  private String accountNumber;

  @Column(name = "code", length = 64)
  private String code;

  @Column(name = "content", columnDefinition = "TEXT")
  private String content;

  @Column(name = "transfer_type", length = 16)
  private String transferType;

  @Column(name = "transfer_amount", precision = 19, scale = 4)
  private BigDecimal transferAmount;

  @Column(name = "accumulated", precision = 19, scale = 4)
  private BigDecimal accumulated;

  @Column(name = "reference_code", length = 128)
  private String referenceCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "processing_status", nullable = false, length = 32)
  private SepayWebhookProcessingStatus processingStatus;

  @Column(name = "raw_payload", columnDefinition = "TEXT")
  private String rawPayload;

  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public SepayWebhookLog() {}

  public SepayWebhookLog(
      UUID id,
      Long sepayTransactionId,
      String gateway,
      String transactionDate,
      String accountNumber,
      String code,
      String content,
      String transferType,
      BigDecimal transferAmount,
      BigDecimal accumulated,
      String referenceCode,
      SepayWebhookProcessingStatus processingStatus,
      String rawPayload,
      String errorMessage,
      Instant createdAt) {
    this.id = id;
    this.sepayTransactionId = sepayTransactionId;
    this.gateway = gateway;
    this.transactionDate = transactionDate;
    this.accountNumber = accountNumber;
    this.code = code;
    this.content = content;
    this.transferType = transferType;
    this.transferAmount = transferAmount;
    this.accumulated = accumulated;
    this.referenceCode = referenceCode;
    this.processingStatus = processingStatus;
    this.rawPayload = rawPayload;
    this.errorMessage = errorMessage;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public Long getSepayTransactionId() {
    return sepayTransactionId;
  }

  public void setSepayTransactionId(Long sepayTransactionId) {
    this.sepayTransactionId = sepayTransactionId;
  }

  public String getGateway() {
    return gateway;
  }

  public void setGateway(String gateway) {
    this.gateway = gateway;
  }

  public String getTransactionDate() {
    return transactionDate;
  }

  public void setTransactionDate(String transactionDate) {
    this.transactionDate = transactionDate;
  }

  public String getAccountNumber() {
    return accountNumber;
  }

  public void setAccountNumber(String accountNumber) {
    this.accountNumber = accountNumber;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public String getTransferType() {
    return transferType;
  }

  public void setTransferType(String transferType) {
    this.transferType = transferType;
  }

  public BigDecimal getTransferAmount() {
    return transferAmount;
  }

  public void setTransferAmount(BigDecimal transferAmount) {
    this.transferAmount = transferAmount;
  }

  public BigDecimal getAccumulated() {
    return accumulated;
  }

  public void setAccumulated(BigDecimal accumulated) {
    this.accumulated = accumulated;
  }

  public String getReferenceCode() {
    return referenceCode;
  }

  public void setReferenceCode(String referenceCode) {
    this.referenceCode = referenceCode;
  }

  public SepayWebhookProcessingStatus getProcessingStatus() {
    return processingStatus;
  }

  public void setProcessingStatus(SepayWebhookProcessingStatus processingStatus) {
    this.processingStatus = processingStatus;
  }

  public String getRawPayload() {
    return rawPayload;
  }

  public void setRawPayload(String rawPayload) {
    this.rawPayload = rawPayload;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
