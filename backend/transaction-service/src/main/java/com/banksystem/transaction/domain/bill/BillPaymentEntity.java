package com.banksystem.transaction.domain.bill;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bill_payments")
public class BillPaymentEntity {

  @Id
  private UUID id;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "category_id", nullable = false, length = 50)
  private String categoryId;

  @Column(name = "provider_id", nullable = false, length = 50)
  private String providerId;

  @Column(name = "customer_code", nullable = false, length = 100)
  private String customerCode;

  @Column(name = "customer_name", length = 150)
  private String customerName;

  @Column(nullable = false, precision = 18, scale = 2)
  private BigDecimal amount;

  @Column(precision = 18, scale = 2)
  private BigDecimal fee = BigDecimal.ZERO;

  @Column(nullable = false, length = 30)
  private String status;

  @Column(name = "transaction_ref", length = 100)
  private String transactionRef;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  // ── Getters & Setters ──

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public UUID getCustomerId() { return customerId; }
  public void setCustomerId(UUID customerId) { this.customerId = customerId; }

  public String getCategoryId() { return categoryId; }
  public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

  public String getProviderId() { return providerId; }
  public void setProviderId(String providerId) { this.providerId = providerId; }

  public String getCustomerCode() { return customerCode; }
  public void setCustomerCode(String customerCode) { this.customerCode = customerCode; }

  public String getCustomerName() { return customerName; }
  public void setCustomerName(String customerName) { this.customerName = customerName; }

  public BigDecimal getAmount() { return amount; }
  public void setAmount(BigDecimal amount) { this.amount = amount; }

  public BigDecimal getFee() { return fee; }
  public void setFee(BigDecimal fee) { this.fee = fee; }

  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }

  public String getTransactionRef() { return transactionRef; }
  public void setTransactionRef(String transactionRef) { this.transactionRef = transactionRef; }

  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
