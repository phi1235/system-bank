package com.banksystem.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "term_deposits")
public class TermDepositEntity {

  @Id
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "source_account_id", nullable = false)
  private UUID sourceAccountId;

  @Column(name = "product_code", nullable = false, length = 20)
  private String productCode;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Column(name = "rate_bps", nullable = false)
  private int rateBps;

  @Column(name = "early_rate_bps", nullable = false)
  private int earlyRateBps;

  @Column(name = "opened_at", nullable = false)
  private Instant openedAt;

  @Column(name = "maturity_date", nullable = false)
  private LocalDate maturityDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TermDepositStatus status = TermDepositStatus.OPEN;

  @Column(name = "accrued_interest", nullable = false, precision = 19, scale = 2)
  private BigDecimal accruedInterest = BigDecimal.ZERO;

  @Column(name = "closed_at")
  private Instant closedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public UUID getSourceAccountId() {
    return sourceAccountId;
  }

  public void setSourceAccountId(UUID sourceAccountId) {
    this.sourceAccountId = sourceAccountId;
  }

  public String getProductCode() {
    return productCode;
  }

  public void setProductCode(String productCode) {
    this.productCode = productCode;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
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

  public Instant getOpenedAt() {
    return openedAt;
  }

  public void setOpenedAt(Instant openedAt) {
    this.openedAt = openedAt;
  }

  public LocalDate getMaturityDate() {
    return maturityDate;
  }

  public void setMaturityDate(LocalDate maturityDate) {
    this.maturityDate = maturityDate;
  }

  public TermDepositStatus getStatus() {
    return status;
  }

  public void setStatus(TermDepositStatus status) {
    this.status = status;
  }

  public BigDecimal getAccruedInterest() {
    return accruedInterest;
  }

  public void setAccruedInterest(BigDecimal accruedInterest) {
    this.accruedInterest = accruedInterest;
  }

  public Instant getClosedAt() {
    return closedAt;
  }

  public void setClosedAt(Instant closedAt) {
    this.closedAt = closedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
