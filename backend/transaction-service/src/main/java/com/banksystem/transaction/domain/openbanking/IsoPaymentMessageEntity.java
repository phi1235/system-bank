package com.banksystem.transaction.domain.openbanking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "iso_payment_messages")
public class IsoPaymentMessageEntity {

  @Id
  private UUID id;

  @Column(name = "message_id", nullable = false, unique = true, length = 128)
  private String messageId;

  @Column(name = "client_id", nullable = false, length = 64)
  private String clientId;

  @Column(name = "message_type", nullable = false, length = 32)
  private String messageType; // PAIN_001, PAIN_002, CAMT_053

  @Column(nullable = false, length = 10)
  private String direction; // INBOUND, OUTBOUND

  @Column(name = "total_transactions", nullable = false)
  private int totalTransactions = 1;

  @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
  private BigDecimal totalAmount = BigDecimal.ZERO;

  @Column(name = "overall_status", nullable = false, length = 20)
  private String overallStatus; // RECEIVED, PROCESSING, COMPLETED, PARTIALLY_COMPLETED, REJECTED

  @Column(name = "raw_payload", nullable = false, columnDefinition = "TEXT")
  private String rawPayload;

  @Column(name = "signature_payload", columnDefinition = "TEXT")
  private String signaturePayload;

  @Column(name = "signature_verified", nullable = false)
  private boolean signatureVerified = false;

  @Column(name = "error_code", length = 32)
  private String errorCode;

  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public static IsoPaymentMessageEntity create(
      UUID id,
      String messageId,
      String clientId,
      String messageType,
      String direction,
      int totalTransactions,
      BigDecimal totalAmount,
      String overallStatus,
      String rawPayload,
      String signaturePayload,
      boolean signatureVerified,
      Instant now) {
    IsoPaymentMessageEntity entity = new IsoPaymentMessageEntity();
    entity.id = id != null ? id : UUID.randomUUID();
    entity.messageId = messageId.trim();
    entity.clientId = clientId.trim();
    entity.messageType = messageType;
    entity.direction = direction;
    entity.totalTransactions = totalTransactions;
    entity.totalAmount = totalAmount != null ? totalAmount : BigDecimal.ZERO;
    entity.overallStatus = overallStatus != null ? overallStatus : "RECEIVED";
    entity.rawPayload = rawPayload != null ? rawPayload : "";
    entity.signaturePayload = signaturePayload;
    entity.signatureVerified = signatureVerified;
    entity.createdAt = now;
    entity.updatedAt = now;
    return entity;
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getMessageId() { return messageId; }
  public void setMessageId(String messageId) { this.messageId = messageId; }
  public String getClientId() { return clientId; }
  public void setClientId(String clientId) { this.clientId = clientId; }
  public String getMessageType() { return messageType; }
  public void setMessageType(String messageType) { this.messageType = messageType; }
  public String getDirection() { return direction; }
  public void setDirection(String direction) { this.direction = direction; }
  public int getTotalTransactions() { return totalTransactions; }
  public void setTotalTransactions(int totalTransactions) { this.totalTransactions = totalTransactions; }
  public BigDecimal getTotalAmount() { return totalAmount; }
  public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
  public String getOverallStatus() { return overallStatus; }
  public void setOverallStatus(String overallStatus) { this.overallStatus = overallStatus; }
  public String getRawPayload() { return rawPayload; }
  public void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }
  public String getSignaturePayload() { return signaturePayload; }
  public void setSignaturePayload(String signaturePayload) { this.signaturePayload = signaturePayload; }
  public boolean isSignatureVerified() { return signatureVerified; }
  public void setSignatureVerified(boolean signatureVerified) { this.signatureVerified = signatureVerified; }
  public String getErrorCode() { return errorCode; }
  public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
  public String getErrorMessage() { return errorMessage; }
  public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
