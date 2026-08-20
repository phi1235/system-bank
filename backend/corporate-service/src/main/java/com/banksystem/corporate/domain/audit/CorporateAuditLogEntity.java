package com.banksystem.corporate.domain.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "corporate_audit_logs")
public class CorporateAuditLogEntity {

  @Id
  private UUID id;

  @Column(name = "corporate_id", nullable = false)
  private UUID corporateId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(nullable = false, length = 64)
  private String action;

  @Column(name = "entity_type", nullable = false, length = 64)
  private String entityType;

  @Column(name = "entity_id", nullable = false, length = 64)
  private String entityId;

  @Column(columnDefinition = "TEXT")
  private String details;

  @Column(name = "ip_address", length = 45)
  private String ipAddress;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  public static CorporateAuditLogEntity of(
      UUID corporateId,
      UUID userId,
      String action,
      String entityType,
      String entityId,
      String details,
      String ipAddress) {
    CorporateAuditLogEntity entity = new CorporateAuditLogEntity();
    entity.id = UUID.randomUUID();
    entity.corporateId = corporateId;
    entity.userId = userId;
    entity.action = action;
    entity.entityType = entityType;
    entity.entityId = entityId;
    entity.details = details;
    entity.ipAddress = ipAddress;
    entity.createdAt = Instant.now();
    return entity;
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public UUID getCorporateId() { return corporateId; }
  public void setCorporateId(UUID corporateId) { this.corporateId = corporateId; }
  public UUID getUserId() { return userId; }
  public void setUserId(UUID userId) { this.userId = userId; }
  public String getAction() { return action; }
  public void setAction(String action) { this.action = action; }
  public String getEntityType() { return entityType; }
  public void setEntityType(String entityType) { this.entityType = entityType; }
  public String getEntityId() { return entityId; }
  public void setEntityId(String entityId) { this.entityId = entityId; }
  public String getDetails() { return details; }
  public void setDetails(String details) { this.details = details; }
  public String getIpAddress() { return ipAddress; }
  public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
