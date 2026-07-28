package com.banksystem.account.domain.entity.card;

import com.banksystem.account.domain.enums.card.CardStatus;
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
@Table(name = "cards")
public class CardEntity {

  @Id
  private UUID id;

  @Column(name = "account_id", nullable = false)
  private UUID accountId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  /** NULL while the card is only REQUESTED — the PAN is generated at approval time. */
  @Column(name = "pan_encrypted")
  private String panEncrypted;

  @Column(name = "pan_last4", length = 4)
  private String panLast4;

  @Column(nullable = false, length = 20)
  private String brand = "NAPAS";

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private CardStatus status = CardStatus.REQUESTED;

  @Column(name = "daily_limit", nullable = false, precision = 19, scale = 2)
  private BigDecimal dailyLimit;

  @Column(name = "expires_on")
  private LocalDate expiresOn;

  @Column(name = "approved_by")
  private UUID approvedBy;

  @Column(name = "approved_at")
  private Instant approvedAt;

  @Column(name = "rejected_by")
  private UUID rejectedBy;

  @Column(name = "rejected_at")
  private Instant rejectedAt;

  @Column(name = "reject_reason", length = 255)
  private String rejectReason;

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

  public UUID getAccountId() {
    return accountId;
  }

  public void setAccountId(UUID accountId) {
    this.accountId = accountId;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public String getPanEncrypted() {
    return panEncrypted;
  }

  public void setPanEncrypted(String panEncrypted) {
    this.panEncrypted = panEncrypted;
  }

  public String getPanLast4() {
    return panLast4;
  }

  public void setPanLast4(String panLast4) {
    this.panLast4 = panLast4;
  }

  public String getBrand() {
    return brand;
  }

  public void setBrand(String brand) {
    this.brand = brand;
  }

  public CardStatus getStatus() {
    return status;
  }

  public void setStatus(CardStatus status) {
    this.status = status;
  }

  public BigDecimal getDailyLimit() {
    return dailyLimit;
  }

  public void setDailyLimit(BigDecimal dailyLimit) {
    this.dailyLimit = dailyLimit;
  }

  public LocalDate getExpiresOn() {
    return expiresOn;
  }

  public void setExpiresOn(LocalDate expiresOn) {
    this.expiresOn = expiresOn;
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

  public UUID getApprovedBy() {
    return approvedBy;
  }

  public void setApprovedBy(UUID approvedBy) {
    this.approvedBy = approvedBy;
  }

  public Instant getApprovedAt() {
    return approvedAt;
  }

  public void setApprovedAt(Instant approvedAt) {
    this.approvedAt = approvedAt;
  }

  public UUID getRejectedBy() {
    return rejectedBy;
  }

  public void setRejectedBy(UUID rejectedBy) {
    this.rejectedBy = rejectedBy;
  }

  public Instant getRejectedAt() {
    return rejectedAt;
  }

  public void setRejectedAt(Instant rejectedAt) {
    this.rejectedAt = rejectedAt;
  }

  public String getRejectReason() {
    return rejectReason;
  }

  public void setRejectReason(String rejectReason) {
    this.rejectReason = rejectReason;
  }
}
