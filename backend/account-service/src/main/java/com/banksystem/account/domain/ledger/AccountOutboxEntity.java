package com.banksystem.account.domain.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_outbox_events")
public class AccountOutboxEntity {

  @Id private UUID id;

  @Column(name = "aggregate_id", nullable = false)
  private UUID aggregateId;

  @Column(name = "event_type", nullable = false, length = 64)
  private String eventType;

  @Column(name = "schema_version", nullable = false)
  private int schemaVersion = 1;

  @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
  private String payload;

  @Column(name = "status", nullable = false, length = 20)
  private String status = "PENDING";

  @Column(name = "retry_count", nullable = false)
  private int retryCount = 0;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  @Column(name = "claimed_by", length = 100)
  private String claimedBy;

  @Column(name = "claimed_at")
  private Instant claimedAt;

  @Column(name = "lease_until")
  private Instant leaseUntil;

  @Column(name = "last_error", length = 2000)
  private String lastError;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "processed_at")
  private Instant processedAt;

  protected AccountOutboxEntity() {}

  public static AccountOutboxEntity create(
      UUID id, UUID aggregateId, String eventType, int schemaVersion, String payload, Instant now) {
    AccountOutboxEntity entity = new AccountOutboxEntity();
    entity.id = id != null ? id : UUID.randomUUID();
    entity.aggregateId = aggregateId;
    entity.eventType = eventType;
    entity.schemaVersion = schemaVersion > 0 ? schemaVersion : 1;
    entity.payload = payload;
    entity.status = "PENDING";
    entity.retryCount = 0;
    entity.nextAttemptAt = now;
    entity.createdAt = now;
    return entity;
  }

  public void markSending(String workerId, Instant now, Instant leaseExpiration) {
    this.status = "SENDING";
    this.claimedBy = workerId;
    this.claimedAt = now;
    this.leaseUntil = leaseExpiration;
  }

  public void markProcessed(Instant now) {
    this.status = "PROCESSED";
    this.processedAt = now;
    this.leaseUntil = null;
  }

  public void markFailed(String errorMessage, Instant nextAttempt, boolean deadLetter) {
    this.retryCount++;
    this.lastError = errorMessage;
    this.leaseUntil = null;
    if (deadLetter) {
      this.status = "DEAD_LETTER";
    } else {
      this.status = "PENDING";
      this.nextAttemptAt = nextAttempt;
    }
  }

  public UUID getId() { return id; }
  public UUID getAggregateId() { return aggregateId; }
  public String getEventType() { return eventType; }
  public int getSchemaVersion() { return schemaVersion; }
  public String getPayload() { return payload; }
  public String getStatus() { return status; }
  public int getRetryCount() { return retryCount; }
  public Instant getNextAttemptAt() { return nextAttemptAt; }
  public String getClaimedBy() { return claimedBy; }
  public Instant getClaimedAt() { return claimedAt; }
  public Instant getLeaseUntil() { return leaseUntil; }
  public String getLastError() { return lastError; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getProcessedAt() { return processedAt; }
}
