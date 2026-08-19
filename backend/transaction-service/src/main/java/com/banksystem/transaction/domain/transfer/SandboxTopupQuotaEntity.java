package com.banksystem.transaction.domain.transfer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "sandbox_topup_quotas")
@IdClass(SandboxTopupQuotaEntity.QuotaId.class)
public class SandboxTopupQuotaEntity {

  public static class QuotaId implements Serializable {
    private UUID userId;
    private LocalDate topupDate;

    public QuotaId() {}

    public QuotaId(UUID userId, LocalDate topupDate) {
      this.userId = userId;
      this.topupDate = topupDate;
    }

    public UUID getUserId() {
      return userId;
    }

    public void setUserId(UUID userId) {
      this.userId = userId;
    }

    public LocalDate getTopupDate() {
      return topupDate;
    }

    public void setTopupDate(LocalDate topupDate) {
      this.topupDate = topupDate;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      QuotaId that = (QuotaId) o;
      return Objects.equals(userId, that.userId) && Objects.equals(topupDate, that.topupDate);
    }

    @Override
    public int hashCode() {
      return Objects.hash(userId, topupDate);
    }
  }

  @Id
  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Id
  @Column(name = "topup_date", nullable = false)
  private LocalDate topupDate;

  @Column(name = "accumulated_amount", nullable = false, precision = 15, scale = 2)
  private BigDecimal accumulatedAmount = BigDecimal.ZERO;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public SandboxTopupQuotaEntity() {}

  public SandboxTopupQuotaEntity(UUID userId, LocalDate topupDate, BigDecimal accumulatedAmount) {
    this.userId = userId;
    this.topupDate = topupDate;
    this.accumulatedAmount = accumulatedAmount;
    this.updatedAt = Instant.now();
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public LocalDate getTopupDate() {
    return topupDate;
  }

  public void setTopupDate(LocalDate topupDate) {
    this.topupDate = topupDate;
  }

  public BigDecimal getAccumulatedAmount() {
    return accumulatedAmount;
  }

  public void setAccumulatedAmount(BigDecimal accumulatedAmount) {
    this.accumulatedAmount = accumulatedAmount;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
