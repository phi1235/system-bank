package com.banksystem.account.domain.entity.deposit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "deposit_products")
public class DepositProductEntity {

  @Id
  @Column(length = 20)
  private String code;

  @Column(name = "tenor_months", nullable = false)
  private int tenorMonths;

  @Column(name = "rate_bps", nullable = false)
  private int rateBps;

  @Column(name = "early_rate_bps", nullable = false)
  private int earlyRateBps;

  @Column(name = "min_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal minAmount;

  @Column(nullable = false)
  private boolean active = true;

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public int getTenorMonths() {
    return tenorMonths;
  }

  public void setTenorMonths(int tenorMonths) {
    this.tenorMonths = tenorMonths;
  }

  public int getRateBps() {
    return rateBps;
  }

  public void setRateBps(int rateBps) {
    this.rateBps = rateBps;
  }

  public int getEarlyRateBps() {
    return earlyRateBps;
  }

  public void setEarlyRateBps(int earlyRateBps) {
    this.earlyRateBps = earlyRateBps;
  }

  public BigDecimal getMinAmount() {
    return minAmount;
  }

  public void setMinAmount(BigDecimal minAmount) {
    this.minAmount = minAmount;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }
}
