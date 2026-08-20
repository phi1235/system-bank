package com.banksystem.corporate.domain.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "corporate_outbox_events")
public class CorporateOutboxEventEntity {

  @Id
  private UUID id;

  @Column(name = "aggregate_type", nullable = false, length = 64)
  private String aggregateType;

  @Column(name = "aggregate_id", nullable = false)
  private UUID aggregateId;

  @Column(name = "event_type", nullable = false, length = 64)
  private String eventType;

  @Column(columnDefinition = "TEXT", nullable = false)
  private String payload;

  @Column(nullable = false, length = 20)
  private String status = "PENDING"; // PENDING, SENDING, PROCESSED, FAILED, DEAD_LETTER

  @Column(name = "retry_count", nullable = false)
  private int retryCount = 0;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt = Instant.now();

  @Column(name = "claimed_by", length = 100)
  private String claimedBy;

  @Column(name = "lease_until")
  private Instant leaseUntil;

  @Column(name = "last_error", length = 2000)
  private String lastError;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "processed_at")
  private Instant processedAt;

  public static CorporateOutboxEventEntity of(
      String aggregateType,
      UUID aggregateId,
      String eventType,
      String payload) {
    CorporateOutboxEventEntity entity = new CorporateOutboxEventEntity();
    entity.id = UUID.randomUUID();
    entity.aggregateType = aggregateType;
    entity.aggregateId = aggregateId;
    entity.eventType = eventType;
    entity.payload = payload;
    entity.status = "PENDING";
    entity.retryCount = 0;
    entity.nextAttemptAt = Instant.now();
    entity.createdAt = Instant.now();
    return entity;
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getAggregateType() { return aggregateType; }
  public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }
  public UUID getAggregateId() { return aggregateId; }
  public void setAggregateId(UUID aggregateId) { this.aggregateId = aggregateId; }
  public String getEventType() { return eventType; }
  public void setEventType(String eventType) { this.eventType = eventType; }
  public String getPayload() { return payload; }
  public void setPayload(String payload) { this.payload = payload; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public int getRetryCount() { return retryCount; }
  public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
  public Instant getNextAttemptAt() { return nextAttemptAt; }
  public void setNextAttemptAt(Instant nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
  public String getClaimedBy() { return claimedBy; }
  public void setClaimedBy(String claimedBy) { this.claimedBy = claimedBy; }
  public Instant getLeaseUntil() { return leaseUntil; }
  public void setLeaseUntil(Instant leaseUntil) { this.leaseUntil = leaseUntil; }
  public String getLastError() { return lastError; }
  public void setLastError(String lastError) { this.lastError = lastError; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getProcessedAt() { return processedAt; }
  public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
}
