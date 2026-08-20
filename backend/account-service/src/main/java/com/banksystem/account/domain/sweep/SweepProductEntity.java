package com.banksystem.account.domain.sweep;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "sweep_products")
public class SweepProductEntity {
  @Id
  @Column(length = 20)
  private String code;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "annual_rate_bps", nullable = false)
  private int annualRateBps;

  @Column(name = "min_threshold", nullable = false, precision = 19, scale = 2)
  private BigDecimal minThreshold;

  @Column(name = "default_threshold", nullable = false, precision = 19, scale = 2)
  private BigDecimal defaultThreshold;

  @Column(name = "min_sweep_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal minSweepAmount;

  @Column(name = "max_position_amount", precision = 19, scale = 2)
  private BigDecimal maxPositionAmount;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public String getCode() { return code; }
  public String getCurrency() { return currency; }
  public int getAnnualRateBps() { return annualRateBps; }
  public BigDecimal getMinThreshold() { return minThreshold; }
  public BigDecimal getDefaultThreshold() { return defaultThreshold; }
  public BigDecimal getMinSweepAmount() { return minSweepAmount; }
  public BigDecimal getMaxPositionAmount() { return maxPositionAmount; }
  public boolean isActive() { return active; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
}
