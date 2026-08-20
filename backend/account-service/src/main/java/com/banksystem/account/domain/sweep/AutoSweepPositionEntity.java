package com.banksystem.account.domain.sweep;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "auto_sweep_positions")
public class AutoSweepPositionEntity {
  @Id private UUID id;
  @Column(name = "profile_id", nullable = false) private UUID profileId;
  @Column(name = "source_account_id", nullable = false) private UUID sourceAccountId;
  @Column(nullable = false, length = 3) private String currency;
  @Column(name = "principal_balance", nullable = false, precision = 19, scale = 2)
  private BigDecimal principalBalance;
  @Column(name = "accrued_interest", nullable = false, precision = 19, scale = 2)
  private BigDecimal accruedInterest;
  @Column(name = "last_accrual_date", nullable = false) private LocalDate lastAccrualDate;
  @Version private long version;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "updated_at", nullable = false) private Instant updatedAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public UUID getProfileId() { return profileId; }
  public void setProfileId(UUID profileId) { this.profileId = profileId; }
  public UUID getSourceAccountId() { return sourceAccountId; }
  public void setSourceAccountId(UUID sourceAccountId) { this.sourceAccountId = sourceAccountId; }
  public String getCurrency() { return currency; }
  public void setCurrency(String currency) { this.currency = currency; }
  public BigDecimal getPrincipalBalance() { return principalBalance; }
  public void setPrincipalBalance(BigDecimal value) { this.principalBalance = value; }
  public BigDecimal getAccruedInterest() { return accruedInterest; }
  public void setAccruedInterest(BigDecimal value) { this.accruedInterest = value; }
  public LocalDate getLastAccrualDate() { return lastAccrualDate; }
  public void setLastAccrualDate(LocalDate value) { this.lastAccrualDate = value; }
  public long getVersion() { return version; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

