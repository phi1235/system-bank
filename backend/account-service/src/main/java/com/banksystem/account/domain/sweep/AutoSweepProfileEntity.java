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
@Table(name = "auto_sweep_profiles")
public class AutoSweepProfileEntity {
  @Id private UUID id;
  @Column(name = "user_id", nullable = false) private UUID userId;
  @Column(name = "source_account_id", nullable = false) private UUID sourceAccountId;
  @Column(name = "product_code", nullable = false, length = 20) private String productCode;
  @Column(name = "threshold_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal thresholdAmount;
  @Column(name = "min_sweep_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal minSweepAmount;
  @Column(nullable = false, length = 20) private String status;
  @Column(name = "last_sweep_business_date") private LocalDate lastSweepBusinessDate;
  @Version private long version;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "updated_at", nullable = false) private Instant updatedAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public UUID getUserId() { return userId; }
  public void setUserId(UUID userId) { this.userId = userId; }
  public UUID getSourceAccountId() { return sourceAccountId; }
  public void setSourceAccountId(UUID sourceAccountId) { this.sourceAccountId = sourceAccountId; }
  public String getProductCode() { return productCode; }
  public void setProductCode(String productCode) { this.productCode = productCode; }
  public BigDecimal getThresholdAmount() { return thresholdAmount; }
  public void setThresholdAmount(BigDecimal thresholdAmount) { this.thresholdAmount = thresholdAmount; }
  public BigDecimal getMinSweepAmount() { return minSweepAmount; }
  public void setMinSweepAmount(BigDecimal minSweepAmount) { this.minSweepAmount = minSweepAmount; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public LocalDate getLastSweepBusinessDate() { return lastSweepBusinessDate; }
  public void setLastSweepBusinessDate(LocalDate value) { this.lastSweepBusinessDate = value; }
  public long getVersion() { return version; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

