package com.banksystem.account.domain.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_journals")
public class LedgerJournalEntity {
  @Id private UUID id;
  @Column(name = "business_command_id", nullable = false, unique = true, length = 160)
  private String businessCommandId;
  @Column(name = "business_reference", nullable = false, length = 100) private String businessReference;
  @Column(name = "transaction_id") private UUID transactionId;
  @Column(name = "journal_type", nullable = false, length = 40) private String journalType;
  @Column(nullable = false, length = 20) private String status;
  @Column(nullable = false, length = 3) private String currency;
  @Column(length = 255) private String description;
  @Column(name = "reversal_of_journal_id") private UUID reversalOfJournalId;
  @Column(name = "sequence_no", nullable = false) private int sequenceNo;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "posted_at") private Instant postedAt;
  @Version @Column(nullable = false) private long version;

  public static LedgerJournalEntity draft(
      UUID id, String commandId, String reference, UUID transactionId, String type,
      String currency, String description, Instant now) {
    return draft(id, commandId, reference, transactionId, type, currency, description, 1, null, now);
  }

  public static LedgerJournalEntity draft(
      UUID id, String commandId, String reference, UUID transactionId, String type,
      String currency, String description, int sequenceNo, UUID reversalOfJournalId, Instant now) {
    LedgerJournalEntity entity = new LedgerJournalEntity();
    entity.id = id;
    entity.businessCommandId = commandId;
    entity.businessReference = reference;
    entity.transactionId = transactionId;
    entity.journalType = type;
    entity.status = "DRAFT";
    entity.currency = currency;
    entity.description = description;
    entity.sequenceNo = sequenceNo;
    entity.reversalOfJournalId = reversalOfJournalId;
    entity.createdAt = now;
    return entity;
  }

  public void post(Instant now) {
    if (!"DRAFT".equals(status)) {
      throw new IllegalStateException("Only draft journals can be posted");
    }
    status = "POSTED";
    postedAt = now;
  }

  public UUID getId() { return id; }
  public String getBusinessCommandId() { return businessCommandId; }
  public String getBusinessReference() { return businessReference; }
  public UUID getTransactionId() { return transactionId; }
  public String getJournalType() { return journalType; }
  public String getStatus() { return status; }
  public String getCurrency() { return currency; }
  public String getDescription() { return description; }
  public UUID getReversalOfJournalId() { return reversalOfJournalId; }
  public int getSequenceNo() { return sequenceNo; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getPostedAt() { return postedAt; }
  public long getVersion() { return version; }
}
