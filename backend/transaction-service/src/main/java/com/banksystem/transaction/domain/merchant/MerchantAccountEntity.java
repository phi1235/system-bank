package com.banksystem.transaction.domain.merchant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "merchant_accounts")
public class MerchantAccountEntity {

  @Id
  private UUID id;

  @Column(name = "organization_id", nullable = false, unique = true)
  private UUID organizationId;

  @Column(name = "collection_account_id", nullable = false)
  private UUID collectionAccountId;

  @Column(name = "escrow_account_id", nullable = false)
  private UUID escrowAccountId;

  @Column(name = "commission_account_id", nullable = false)
  private UUID commissionAccountId;

  @Column(name = "default_currency", nullable = false, length = 3)
  private String defaultCurrency = "VND";

  @Column(nullable = false, length = 30)
  private String status = "ACTIVE";

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public static MerchantAccountEntity create(
      UUID organizationId, UUID collectionAccountId, UUID escrowAccountId, UUID commissionAccountId, String currency, Instant now) {
    MerchantAccountEntity entity = new MerchantAccountEntity();
    entity.id = UUID.randomUUID();
    entity.organizationId = organizationId;
    entity.collectionAccountId = collectionAccountId;
    entity.escrowAccountId = escrowAccountId;
    entity.commissionAccountId = commissionAccountId;
    entity.defaultCurrency = currency != null ? currency.toUpperCase() : "VND";
    entity.status = "ACTIVE";
    entity.createdAt = now;
    entity.updatedAt = now;
    return entity;
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public UUID getOrganizationId() { return organizationId; }
  public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
  public UUID getCollectionAccountId() { return collectionAccountId; }
  public void setCollectionAccountId(UUID collectionAccountId) { this.collectionAccountId = collectionAccountId; }
  public UUID getEscrowAccountId() { return escrowAccountId; }
  public void setEscrowAccountId(UUID escrowAccountId) { this.escrowAccountId = escrowAccountId; }
  public UUID getCommissionAccountId() { return commissionAccountId; }
  public void setCommissionAccountId(UUID commissionAccountId) { this.commissionAccountId = commissionAccountId; }
  public String getDefaultCurrency() { return defaultCurrency; }
  public void setDefaultCurrency(String defaultCurrency) { this.defaultCurrency = defaultCurrency; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
