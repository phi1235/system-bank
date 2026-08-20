package com.banksystem.auth.domain.business;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "business_members")
public class BusinessMemberEntity {

  @Id
  private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "business_role", nullable = false, length = 50)
  private String businessRole;

  @Column(nullable = false, length = 30)
  private String status = "ACTIVE";

  @Column(name = "joined_at", nullable = false)
  private Instant joinedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public static BusinessMemberEntity create(UUID organizationId, UUID userId, String businessRole, Instant now) {
    BusinessMemberEntity entity = new BusinessMemberEntity();
    entity.id = UUID.randomUUID();
    entity.organizationId = organizationId;
    entity.userId = userId;
    entity.businessRole = businessRole;
    entity.status = "ACTIVE";
    entity.joinedAt = now;
    entity.createdAt = now;
    entity.updatedAt = now;
    return entity;
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public UUID getOrganizationId() { return organizationId; }
  public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
  public UUID getUserId() { return userId; }
  public void setUserId(UUID userId) { this.userId = userId; }
  public String getBusinessRole() { return businessRole; }
  public void setBusinessRole(String businessRole) { this.businessRole = businessRole; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getJoinedAt() { return joinedAt; }
  public void setJoinedAt(Instant joinedAt) { this.joinedAt = joinedAt; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
