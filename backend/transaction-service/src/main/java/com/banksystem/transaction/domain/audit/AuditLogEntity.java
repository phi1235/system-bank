package com.banksystem.transaction.domain.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLogEntity {

  @Id
  private UUID id;

  @Column(name = "actor_user_id")
  private UUID actorUserId;

  @Column(nullable = false, length = 50)
  private String action;

  @Column(name = "resource_type", length = 50)
  private String resourceType;

  @Column(name = "resource_id", length = 64)
  private String resourceId;

  @Column(length = 64)
  private String ip;

  @Column(columnDefinition = "TEXT")
  private String metadata;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  public static AuditLogEntity of(
      UUID actor, String action, String resourceType, String resourceId, String ip, String metadata) {
    AuditLogEntity e = new AuditLogEntity();
    e.id = UUID.randomUUID();
    e.actorUserId = actor;
    e.action = action;
    e.resourceType = resourceType;
    e.resourceId = resourceId;
    e.ip = ip;
    e.metadata = metadata;
    e.createdAt = Instant.now();
    return e;
  }

  public UUID getId() {
    return id;
  }

  public UUID getActorUserId() {
    return actorUserId;
  }

  public String getAction() {
    return action;
  }

  public String getResourceType() {
    return resourceType;
  }

  public String getResourceId() {
    return resourceId;
  }

  public String getIp() {
    return ip;
  }

  public String getMetadata() {
    return metadata;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
