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
@Table(name = "sepay_payment_orders")
public class SepayPaymentOrder {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "order_code", nullable = false, unique = true, length = 32)
  private String orderCode;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "account_number", nullable = false, length = 32)
  private String accountNumber;

  @Column(name = "amount", nullable = false, precision = 19, scale = 4)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 32)
  private SepayOrderStatus status;

  @Column(name = "viet_qr_url", columnDefinition = "TEXT")
  private String vietQrUrl;

  @Column(name = "sepay_transaction_id")
  private Long sepayTransactionId;

  @Column(name = "bank_brand_name", length = 64)
  private String bankBrandName;

  @Column(name = "transfer_content", length = 255)
  private String transferContent;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  public SepayPaymentOrder() {}

  public SepayPaymentOrder(
      UUID id,
      String orderCode,
      UUID userId,
      String accountNumber,
      BigDecimal amount,
      SepayOrderStatus status,
      String vietQrUrl,
      String bankBrandName,
      String transferContent,
      Instant createdAt,
      Instant expiresAt) {
    this.id = id;
    this.orderCode = orderCode;
    this.userId = userId;
    this.accountNumber = accountNumber;
    this.amount = amount;
    this.status = status;
    this.vietQrUrl = vietQrUrl;
    this.bankBrandName = bankBrandName;
    this.transferContent = transferContent;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getOrderCode() {
    return orderCode;
  }

  public void setOrderCode(String orderCode) {
    this.orderCode = orderCode;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public String getAccountNumber() {
    return accountNumber;
  }

  public void setAccountNumber(String accountNumber) {
    this.accountNumber = accountNumber;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public SepayOrderStatus getStatus() {
    return status;
  }

  public void setStatus(SepayOrderStatus status) {
    this.status = status;
  }

  public String getVietQrUrl() {
    return vietQrUrl;
  }

  public void setVietQrUrl(String vietQrUrl) {
    this.vietQrUrl = vietQrUrl;
  }

  public Long getSepayTransactionId() {
    return sepayTransactionId;
  }

  public void setSepayTransactionId(Long sepayTransactionId) {
    this.sepayTransactionId = sepayTransactionId;
  }

  public String getBankBrandName() {
    return bankBrandName;
  }

  public void setBankBrandName(String bankBrandName) {
    this.bankBrandName = bankBrandName;
  }

  public String getTransferContent() {
    return transferContent;
  }

  public void setTransferContent(String transferContent) {
    this.transferContent = transferContent;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(Instant completedAt) {
    this.completedAt = completedAt;
  }
}
