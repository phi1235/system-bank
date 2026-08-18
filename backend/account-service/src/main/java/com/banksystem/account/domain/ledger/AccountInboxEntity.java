package com.banksystem.account.domain.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_inbox_events")
public class AccountInboxEntity {

  @Id private UUID eventId;

  @Column(name = "event_type", nullable = false, length = 64)
  private String eventType;

  @Column(name = "processed_at", nullable = false)
  private Instant processedAt;

  protected AccountInboxEntity() {}

  public AccountInboxEntity(UUID eventId, String eventType, Instant processedAt) {
    this.eventId = eventId;
    this.eventType = eventType;
    this.processedAt = processedAt != null ? processedAt : Instant.now();
  }

  public UUID getEventId() { return eventId; }
  public String getEventType() { return eventType; }
  public Instant getProcessedAt() { return processedAt; }
}
