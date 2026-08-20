package com.banksystem.corporate.domain.corporation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "corporate_member_roles")
public class CorporateMemberRoleEntity {

  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "membership_id", nullable = false)
  private CorporateMembershipEntity membership;

  @Column(name = "role_name", nullable = false, length = 50)
  private String roleName;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  public CorporateMemberRoleEntity() {}

  public CorporateMemberRoleEntity(UUID id, CorporateMembershipEntity membership, String roleName) {
    this.id = id;
    this.membership = membership;
    this.roleName = roleName;
    this.createdAt = Instant.now();
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public CorporateMembershipEntity getMembership() { return membership; }
  public void setMembership(CorporateMembershipEntity membership) { this.membership = membership; }
  public String getRoleName() { return roleName; }
  public void setRoleName(String roleName) { this.roleName = roleName; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
