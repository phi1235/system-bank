package com.banksystem.account.domain.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_temporal_snapshots")
public class AccountTemporalSnapshotEntity {
  @Id private UUID id;
  @Column(name = "account_id", nullable = false) private UUID accountId;
  @Column(name = "snapshot_at", nullable = false) private Instant snapshotAt;
  @Column(name = "ledger_balance", nullable = false) private BigDecimal ledgerBalance;
  @Column(name = "active_hold_amount", nullable = false) private BigDecimal activeHoldAmount;
  @Column(name = "last_entry_at") private Instant lastEntryAt;
  @Column(name = "schema_version", nullable = false) private int schemaVersion;
  @Column(nullable = false, length = 64) private String checksum;
  @Column(name = "created_at", nullable = false) private Instant createdAt;

  public static AccountTemporalSnapshotEntity create(
      UUID accountId, Instant at, BigDecimal balance, BigDecimal held,
      Instant lastEntryAt, String checksum) {
    AccountTemporalSnapshotEntity entity = new AccountTemporalSnapshotEntity();
    entity.id = UUID.randomUUID();
    entity.accountId = accountId;
    entity.snapshotAt = at;
    entity.ledgerBalance = balance;
    entity.activeHoldAmount = held;
    entity.lastEntryAt = lastEntryAt;
    entity.schemaVersion = 1;
    entity.checksum = checksum;
    entity.createdAt = at;
    return entity;
  }

  public UUID getAccountId() { return accountId; }
  public Instant getSnapshotAt() { return snapshotAt; }
  public BigDecimal getLedgerBalance() { return ledgerBalance; }
  public BigDecimal getActiveHoldAmount() { return activeHoldAmount; }
  public Instant getLastEntryAt() { return lastEntryAt; }
  public int getSchemaVersion() { return schemaVersion; }
  public String getChecksum() { return checksum; }
}
