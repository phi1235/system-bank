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
    capturedJournalId = journalId;
    updatedAt = now;
    return true;
  }

  public boolean release(Instant now) {
    if ("RELEASED".equals(status)) return false;
    requireActive(now);
    status = "RELEASED";
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
  public String getCommandId() { return commandId; }
  public BigDecimal getAmount() { return amount; }
  public String getCurrency() { return currency; }
  public String getStatus() { return status; }
  public Instant getExpiresAt() { return expiresAt; }
  public UUID getCapturedJournalId() { return capturedJournalId; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public long getVersion() { return version; }
}
