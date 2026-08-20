package com.banksystem.corporate.domain.corporation;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "corporate_memberships")
public class CorporateMembershipEntity {

  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "corporate_id", nullable = false)
  private CorporationEntity corporation;

  @Column(name = "corporate_id", insertable = false, updatable = false)
  private UUID corporateId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(nullable = false, length = 30)
  private String status = "ACTIVE";

  @Column(name = "joined_at", nullable = false)
  private Instant joinedAt = Instant.now();

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @Version
  @Column(nullable = false)
  private long version;

  @OneToMany(mappedBy = "membership", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
  private Set<CorporateMemberRoleEntity> roles = new HashSet<>();

  public boolean hasRole(String roleName) {
    if (roles == null || roleName == null) return false;
    return roles.stream().anyMatch(r -> roleName.equalsIgnoreCase(r.getRoleName()));
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public CorporationEntity getCorporation() { return corporation; }
  public void setCorporation(CorporationEntity corporation) {
    this.corporation = corporation;
    if (corporation != null) {
      this.corporateId = corporation.getId();
    }
  }
  public UUID getCorporateId() { return corporateId; }
  public void setCorporateId(UUID corporateId) { this.corporateId = corporateId; }
  public UUID getUserId() { return userId; }
  public void setUserId(UUID userId) { this.userId = userId; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getJoinedAt() { return joinedAt; }
  public void setJoinedAt(Instant joinedAt) { this.joinedAt = joinedAt; }
  public Instant getExpiresAt() { return expiresAt; }
  public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
  public long getVersion() { return version; }
  public void setVersion(long version) { this.version = version; }
  public Set<CorporateMemberRoleEntity> getRoles() { return roles; }
  public void setRoles(Set<CorporateMemberRoleEntity> roles) { this.roles = roles; }
}
