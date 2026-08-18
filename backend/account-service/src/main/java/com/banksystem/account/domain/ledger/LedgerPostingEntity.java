package com.banksystem.account.domain.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_postings")
public class LedgerPostingEntity {
  @Id private UUID id;
  @Column(name = "journal_id", nullable = false) private UUID journalId;
  @Column(name = "account_id") private UUID accountId;
  @Column(name = "ledger_account_code", nullable = false, length = 80) private String ledgerAccountCode;
  @Column(nullable = false, length = 10) private String side;
  @Column(nullable = false, precision = 19, scale = 2) private BigDecimal amount;
  @Column(nullable = false, length = 3) private String currency;
  @Column(name = "created_at", nullable = false) private Instant createdAt;

  public static LedgerPostingEntity of(
      UUID journalId, UUID accountId, String accountCode, String side,
      BigDecimal amount, String currency, Instant now) {
    LedgerPostingEntity entity = new LedgerPostingEntity();
    entity.id = UUID.randomUUID();
    entity.journalId = journalId;
    entity.accountId = accountId;
    entity.ledgerAccountCode = accountCode;
    entity.side = side;
    entity.amount = amount;
    entity.currency = currency;
    entity.createdAt = now;
    return entity;
  }

  public UUID getId() { return id; }
  public UUID getJournalId() { return journalId; }
  public UUID getAccountId() { return accountId; }
  public String getLedgerAccountCode() { return ledgerAccountCode; }
  public String getSide() { return side; }
  public BigDecimal getAmount() { return amount; }
  public String getCurrency() { return currency; }
  public Instant getCreatedAt() { return createdAt; }
}
