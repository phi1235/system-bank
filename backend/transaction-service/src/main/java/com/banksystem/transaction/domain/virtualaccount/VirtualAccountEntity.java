package com.banksystem.transaction.domain.virtualaccount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "virtual_accounts")
public class VirtualAccountEntity {

  @Id
  private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(nullable = false, length = 50)
  private String provider;

  @Column(name = "bank_bin", nullable = false, length = 20)
  private String bankBin;

  @Column(name = "account_number", nullable = false, length = 50)
  private String accountNumber;

  @Column(name = "parent_account_id")
  private UUID parentAccountId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private VirtualAccountMode mode;

  @Column(name = "customer_reference", length = 100)
  private String customerReference;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private VirtualAccountStatus status = VirtualAccountStatus.ACTIVE;

  @Column(name = "activated_at", nullable = false)
  private Instant activatedAt;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public static VirtualAccountEntity create(
      UUID organizationId,
      String provider,
      String bankBin,
      String accountNumber,
      UUID parentAccountId,
      VirtualAccountMode mode,
      String customerReference,
      Instant expiresAt,
      Instant now) {
    VirtualAccountEntity entity = new VirtualAccountEntity();
    entity.id = UUID.randomUUID();
    entity.organizationId = organizationId;
    entity.provider = provider.toUpperCase();
    entity.bankBin = bankBin;
    entity.accountNumber = accountNumber;
    entity.parentAccountId = parentAccountId;
    entity.mode = mode;
    entity.customerReference = customerReference;
    entity.status = VirtualAccountStatus.ACTIVE;
    entity.activatedAt = now;
    entity.expiresAt = expiresAt;
    entity.createdAt = now;
    entity.updatedAt = now;
    return entity;
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public UUID getOrganizationId() { return organizationId; }
  public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
  public String getProvider() { return provider; }
  public void setProvider(String provider) { this.provider = provider; }
  public String getBankBin() { return bankBin; }
  public void setBankBin(String bankBin) { this.bankBin = bankBin; }
  public String getAccountNumber() { return accountNumber; }
  public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
  public UUID getParentAccountId() { return parentAccountId; }
  public void setParentAccountId(UUID parentAccountId) { this.parentAccountId = parentAccountId; }
  public VirtualAccountMode getMode() { return mode; }
  public void setMode(VirtualAccountMode mode) { this.mode = mode; }
  public String getCustomerReference() { return customerReference; }
  public void setCustomerReference(String customerReference) { this.customerReference = customerReference; }
  public VirtualAccountStatus getStatus() { return status; }
  public void setStatus(VirtualAccountStatus status) { this.status = status; }
  public Instant getActivatedAt() { return activatedAt; }
  public void setActivatedAt(Instant activatedAt) { this.activatedAt = activatedAt; }
  public Instant getExpiresAt() { return expiresAt; }
  public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
