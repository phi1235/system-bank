package com.banksystem.transaction.domain.settlement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "settlement_audit_logs")
public class SettlementAuditLogEntity {

  @Id
  private UUID id;

  @Column(name = "settlement_id", nullable = false)
  private UUID settlementId;

  @Column(nullable = false, length = 50)
  private String action;

  @Column(name = "actor_id")
  private UUID actorId;

  @Column(name = "actor_role", length = 50)
  private String actorRole;

  @Column(columnDefinition = "TEXT")
  private String details;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public static SettlementAuditLogEntity create(
      UUID settlementId, String action, UUID actorId, String actorRole, String details, Instant now) {
    SettlementAuditLogEntity entity = new SettlementAuditLogEntity();
    entity.id = UUID.randomUUID();
    entity.settlementId = settlementId;
    entity.action = action;
    entity.actorId = actorId;
    entity.actorRole = actorRole;
    entity.details = details;
    entity.createdAt = now;
    return entity;
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public UUID getSettlementId() { return settlementId; }
  public void setSettlementId(UUID settlementId) { this.settlementId = settlementId; }
  public String getAction() { return action; }
  public void setAction(String action) { this.action = action; }
  public UUID getActorId() { return actorId; }
  public void setActorId(UUID actorId) { this.actorId = actorId; }
  public String getActorRole() { return actorRole; }
  public void setActorRole(String actorRole) { this.actorRole = actorRole; }
  public String getDetails() { return details; }
  public void setDetails(String details) { this.details = details; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
