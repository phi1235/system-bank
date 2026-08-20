package com.banksystem.corporate.domain.corporation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "corporate_accounts")
public class CorporateAccountEntity {

  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "corporate_id", nullable = false)
  private CorporationEntity corporation;

  @Column(name = "corporate_id", insertable = false, updatable = false)
  private UUID corporateId;

  @Column(name = "account_id", nullable = false)
  private UUID accountId;

  @Column(name = "account_number", nullable = false, length = 20)
  private String accountNumber;

  @Column(name = "account_name", length = 160)
  private String accountName;

  @Column(nullable = false, length = 3)
  private String currency = "VND";

  @Column(name = "is_primary", nullable = false)
  private boolean isPrimary = false;

  @Column(nullable = false, length = 30)
  private String status = "ACTIVE";

  @Column(name = "daily_payout_limit", precision = 19, scale = 2)
  private BigDecimal dailyPayoutLimit;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public CorporationEntity getCorporation() { return corporation; }
  public void setCorporation(CorporationEntity corporation) {
    this.corporation = corporation;
    if (corporation != null) {
      this.corporateId = corporation.getId();
    }
  }
  public UUID getCorporateId() { return corporateId; }
  public void setCorporateId(UUID corporateId) { this.corporateId = corporateId; }
  public UUID getAccountId() { return accountId; }
  public void setAccountId(UUID accountId) { this.accountId = accountId; }
  public String getAccountNumber() { return accountNumber; }
  public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
  public String getAccountName() { return accountName; }
  public void setAccountName(String accountName) { this.accountName = accountName; }
  public String getCurrency() { return currency; }
  public void setCurrency(String currency) { this.currency = currency; }
  public boolean isPrimary() { return isPrimary; }
  public void setPrimary(boolean primary) { isPrimary = primary; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public BigDecimal getDailyPayoutLimit() { return dailyPayoutLimit; }
  public void setDailyPayoutLimit(BigDecimal dailyPayoutLimit) { this.dailyPayoutLimit = dailyPayoutLimit; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
