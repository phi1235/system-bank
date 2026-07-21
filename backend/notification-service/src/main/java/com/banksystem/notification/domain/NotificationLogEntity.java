package com.banksystem.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_logs")
public class NotificationLogEntity {

  @Id
  private UUID id;

  @Column(name = "event_id", nullable = false, unique = true)
  private UUID eventId;

  @Column(nullable = false, length = 10)
  private String channel;

  @Column(nullable = false)
  private String recipient;

  @Column(nullable = false, length = 50)
  private String template;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(columnDefinition = "TEXT")
  private String body;

  /** Owning customer user id for IB inbox (nullable for legacy / system / OPS rows). */
  @Column(name = "user_id")
  private UUID userId;

  /**
   * Inbox audience: {@code CUSTOMER} (user-scoped IB) or {@code OPS} (shared staff alerts).
   */
  @Column(name = "audience", nullable = false, length = 20)
  private String audience = "CUSTOMER";

  @Column(name = "read_at")
  private Instant readAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getEventId() {
    return eventId;
  }

  public void setEventId(UUID eventId) {
    this.eventId = eventId;
  }

  public String getChannel() {
    return channel;
  }

  public void setChannel(String channel) {
    this.channel = channel;
  }

  public String getRecipient() {
    return recipient;
  }

  public void setRecipient(String recipient) {
    this.recipient = recipient;
  }

  public String getTemplate() {
    return template;
  }

  public void setTemplate(String template) {
    this.template = template;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getBody() {
    return body;
  }

  public void setBody(String body) {
    this.body = body;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public String getAudience() {
    return audience;
  }

  public void setAudience(String audience) {
    this.audience = audience;
  }

  public Instant getReadAt() {
    return readAt;
  }

  public void setReadAt(Instant readAt) {
    this.readAt = readAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
