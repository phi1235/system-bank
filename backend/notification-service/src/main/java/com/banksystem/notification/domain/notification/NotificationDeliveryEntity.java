package com.banksystem.notification.domain.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_deliveries")
public class NotificationDeliveryEntity {

  public static final String CHANNEL_EMAIL = "EMAIL";
  public static final String CHANNEL_SMS = "SMS";
  public static final String STATUS_PENDING = "PENDING";
  public static final String STATUS_SENT = "SENT";
  public static final String STATUS_DEAD = "DEAD";

  @Id
  private UUID id;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(nullable = false, length = 10)
  private String channel;

  @Column(nullable = false)
  private String destination;

  @Column(nullable = false)
  private String subject;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String body;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  @Column(name = "last_error", length = 500)
  private String lastError;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "sent_at")
  private Instant sentAt;

  protected NotificationDeliveryEntity() {
  }

  public static NotificationDeliveryEntity pending(
      UUID id,
      UUID eventId,
      String channel,
      String destination,
      String subject,
      String body,
      Instant now) {
    NotificationDeliveryEntity delivery = new NotificationDeliveryEntity();
    delivery.id = id;
    delivery.eventId = eventId;
    delivery.channel = channel;
    delivery.destination = destination;
    delivery.subject = subject;
    delivery.body = body;
    delivery.status = STATUS_PENDING;
    delivery.nextAttemptAt = now;
    delivery.createdAt = now;
    delivery.updatedAt = now;
    return delivery;
  }

  public void markSent(Instant now) {
    status = STATUS_SENT;
    attemptCount++;
    sentAt = now;
    updatedAt = now;
    lastError = null;
  }

  public void markRetry(Instant now, Instant retryAt, String error) {
    status = STATUS_PENDING;
    attemptCount++;
    nextAttemptAt = retryAt;
    updatedAt = now;
    lastError = error;
  }

  public void markDead(Instant now, String error) {
    status = STATUS_DEAD;
    attemptCount++;
    updatedAt = now;
    lastError = error;
  }

  public UUID getId() { return id; }
  public UUID getEventId() { return eventId; }
  public String getChannel() { return channel; }
  public String getDestination() { return destination; }
  public String getSubject() { return subject; }
  public String getBody() { return body; }
  public String getStatus() { return status; }
  public int getAttemptCount() { return attemptCount; }
  public Instant getNextAttemptAt() { return nextAttemptAt; }
  public String getLastError() { return lastError; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public Instant getSentAt() { return sentAt; }
}
