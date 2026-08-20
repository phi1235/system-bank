package com.banksystem.transaction.domain.collection;

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
@Table(name = "collection_orders")
public class CollectionOrderEntity {

  @Id
  private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "merchant_order_id", nullable = false, length = 100)
  private String merchantOrderId;

  @Column(name = "virtual_account_id", nullable = false)
  private UUID virtualAccountId;

  @Column(name = "expected_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal expectedAmount;

  @Column(name = "paid_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal paidAmount = BigDecimal.ZERO;

  @Column(nullable = false, length = 3)
  private String currency = "VND";

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private CollectionOrderStatus status = CollectionOrderStatus.PENDING;

  @Column(name = "customer_reference", length = 100)
  private String customerReference;

  @Column(name = "split_rule_snapshot", columnDefinition = "TEXT")
  private String splitRuleSnapshot;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "paid_at")
  private Instant paidAt;

  @Version
  @Column(nullable = false)
  private long version;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public static CollectionOrderEntity create(
      UUID organizationId,
      String merchantOrderId,
      UUID virtualAccountId,
      BigDecimal expectedAmount,
      String currency,
      String customerReference,
      String splitRuleSnapshot,
      Instant expiresAt,
      Instant now) {
    CollectionOrderEntity entity = new CollectionOrderEntity();
    entity.id = UUID.randomUUID();
    entity.organizationId = organizationId;
    entity.merchantOrderId = merchantOrderId;
    entity.virtualAccountId = virtualAccountId;
    entity.expectedAmount = expectedAmount;
    entity.paidAmount = BigDecimal.ZERO;
    entity.currency = currency != null ? currency.toUpperCase() : "VND";
    entity.status = CollectionOrderStatus.PENDING;
    entity.customerReference = customerReference;
    entity.splitRuleSnapshot = splitRuleSnapshot;
    entity.expiresAt = expiresAt;
    entity.createdAt = now;
    entity.updatedAt = now;
    return entity;
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public UUID getOrganizationId() { return organizationId; }
  public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
  public String getMerchantOrderId() { return merchantOrderId; }
  public void setMerchantOrderId(String merchantOrderId) { this.merchantOrderId = merchantOrderId; }
  public UUID getVirtualAccountId() { return virtualAccountId; }
  public void setVirtualAccountId(UUID virtualAccountId) { this.virtualAccountId = virtualAccountId; }
  public BigDecimal getExpectedAmount() { return expectedAmount; }
  public void setExpectedAmount(BigDecimal expectedAmount) { this.expectedAmount = expectedAmount; }
  public BigDecimal getPaidAmount() { return paidAmount; }
  public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }
  public String getCurrency() { return currency; }
  public void setCurrency(String currency) { this.currency = currency; }
  public CollectionOrderStatus getStatus() { return status; }
  public void setStatus(CollectionOrderStatus status) { this.status = status; }
  public String getCustomerReference() { return customerReference; }
  public void setCustomerReference(String customerReference) { this.customerReference = customerReference; }
  public String getSplitRuleSnapshot() { return splitRuleSnapshot; }
  public void setSplitRuleSnapshot(String splitRuleSnapshot) { this.splitRuleSnapshot = splitRuleSnapshot; }
  public Instant getExpiresAt() { return expiresAt; }
  public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
  public Instant getPaidAt() { return paidAt; }
  public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }
  public long getVersion() { return version; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
