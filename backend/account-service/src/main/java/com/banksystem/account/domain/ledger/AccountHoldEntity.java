package com.banksystem.account.domain.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_holds")
public class AccountHoldEntity {
  @Id private UUID id;
  @Column(name = "account_id", nullable = false) private UUID accountId;
  @Column(name = "transaction_id", nullable = false) private UUID transactionId;
  @Column(name = "command_id", nullable = false, length = 160) private String commandId;
  @Column(name = "original_amount", precision = 19, scale = 2) private BigDecimal originalAmount;
  @Column(name = "captured_amount", nullable = false, precision = 19, scale = 2) private BigDecimal capturedAmount = BigDecimal.ZERO;
  @Column(name = "released_amount", nullable = false, precision = 19, scale = 2) private BigDecimal releasedAmount = BigDecimal.ZERO;
  @Column(name = "batch_id") private UUID batchId;
  @Column(nullable = false, precision = 19, scale = 2) private BigDecimal amount;
  @Column(nullable = false, length = 3) private String currency;
  @Column(nullable = false, length = 20) private String status;
  @Column(name = "expires_at", nullable = false) private Instant expiresAt;
  @Column(name = "captured_journal_id") private UUID capturedJournalId;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "updated_at", nullable = false) private Instant updatedAt;
  @Version @Column(nullable = false) private long version;

  public static AccountHoldEntity active(
      UUID id,
      UUID accountId,
      UUID transactionId,
      String commandId,
      BigDecimal amount,
      String currency,
      Instant expiresAt,
      Instant now) {
    AccountHoldEntity entity = new AccountHoldEntity();
    entity.id = id;
    entity.accountId = accountId;
    entity.transactionId = transactionId;
    entity.commandId = commandId;
    entity.amount = amount;
    entity.originalAmount = amount;
    entity.capturedAmount = BigDecimal.ZERO;
    entity.releasedAmount = BigDecimal.ZERO;
    entity.currency = currency;
    entity.status = "ACTIVE";
    entity.expiresAt = expiresAt;
    entity.createdAt = now;
    entity.updatedAt = now;
    return entity;
  }

  public static AccountHoldEntity activeForBatch(
      UUID id,
      UUID accountId,
      UUID batchId,
      String commandId,
      BigDecimal amount,
      String currency,
      Instant expiresAt,
      Instant now) {
    AccountHoldEntity entity = new AccountHoldEntity();
    entity.id = id;
    entity.accountId = accountId;
    entity.transactionId = batchId;
    entity.batchId = batchId;
    entity.commandId = commandId;
    entity.amount = amount;
    entity.originalAmount = amount;
    entity.capturedAmount = BigDecimal.ZERO;
    entity.releasedAmount = BigDecimal.ZERO;
    entity.currency = currency;
    entity.status = "ACTIVE";
    entity.expiresAt = expiresAt;
    entity.createdAt = now;
    entity.updatedAt = now;
    return entity;
  }

  public boolean capture(UUID journalId, Instant now) {
    if ("CAPTURED".equals(status)) return false;
    requireActive(now);
    status = "CAPTURED";
    capturedAmount = amount;
    capturedJournalId = journalId;
    updatedAt = now;
    return true;
  }

  public boolean partialCapture(BigDecimal captureDelta, Instant now) {
    requireActive(now);
    if (captureDelta == null || captureDelta.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("Capture amount must be positive");
    }
    BigDecimal newCaptured = capturedAmount.add(captureDelta);
    if (newCaptured.compareTo(originalAmount != null ? originalAmount : amount) > 0) {
      throw new IllegalStateException("Captured amount cannot exceed original hold amount");
    }
    capturedAmount = newCaptured;
    updatedAt = now;
    return true;
  }

  public boolean reverseCapture(BigDecimal captureDelta, Instant now) {
    if (captureDelta == null || captureDelta.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("Capture reversal amount must be positive");
    }
    if (capturedAmount.compareTo(captureDelta) < 0) {
      throw new IllegalStateException("Capture reversal cannot exceed captured amount");
    }
    capturedAmount = capturedAmount.subtract(captureDelta);
    if ("RELEASED".equals(status)) {
      releasedAmount = releasedAmount.add(captureDelta);
    }
    updatedAt = now;
    return true;
  }

  public boolean releaseRemaining(Instant now) {
    if ("RELEASED".equals(status) || "CAPTURED".equals(status)) return false;
    requireActive(now);
    status = "RELEASED";
    BigDecimal orig = originalAmount != null ? originalAmount : amount;
    releasedAmount = orig.subtract(capturedAmount);
    updatedAt = now;
    return true;
  }

  public boolean release(Instant now) {
    if ("RELEASED".equals(status)) return false;
    requireActive(now);
    status = "RELEASED";
    releasedAmount = amount;
    updatedAt = now;
    return true;
  }

  public boolean expire(Instant now) {
    if (!"ACTIVE".equals(status) || expiresAt.isAfter(now)) return false;
    status = "EXPIRED";
    updatedAt = now;
    return true;
  }

  private void requireActive(Instant now) {
    if (!"ACTIVE".equals(status)) {
      throw new IllegalStateException("Hold is not active");
    }
    if (!expiresAt.isAfter(now)) {
      throw new IllegalStateException("Hold has expired");
    }
  }

  public UUID getId() { return id; }
  public UUID getAccountId() { return accountId; }
  public UUID getTransactionId() { return transactionId; }
  public UUID getBatchId() { return batchId; }
  public void setBatchId(UUID batchId) { this.batchId = batchId; }
  public String getCommandId() { return commandId; }
  public BigDecimal getAmount() { return amount; }
  public BigDecimal getOriginalAmount() { return originalAmount; }
  public BigDecimal getCapturedAmount() { return capturedAmount; }
  public BigDecimal getReleasedAmount() { return releasedAmount; }
  public String getCurrency() { return currency; }
  public String getStatus() { return status; }
  public Instant getExpiresAt() { return expiresAt; }
  public UUID getCapturedJournalId() { return capturedJournalId; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public long getVersion() { return version; }
}
