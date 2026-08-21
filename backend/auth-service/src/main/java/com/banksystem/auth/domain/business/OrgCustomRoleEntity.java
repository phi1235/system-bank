package com.banksystem.auth.domain.business;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "org_custom_roles")
public class OrgCustomRoleEntity {

  @Id
  private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(nullable = false, length = 50)
  private String code;

  @Column(name = "display_name", nullable = false, length = 100)
  private String displayName;

  @Column(length = 255)
  private String description;

  @Column(name = "is_owner_role", nullable = false)
  private boolean ownerRole;

  @Column(name = "is_default", nullable = false)
  private boolean defaultRole;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @OneToMany(mappedBy = "role", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
  private Set<OrgCustomRolePermissionEntity> permissions = new HashSet<>();

  public static OrgCustomRoleEntity create(UUID organizationId, String code, String displayName,
      String description, boolean ownerRole, boolean defaultRole, Instant now) {
    OrgCustomRoleEntity entity = new OrgCustomRoleEntity();
    entity.id = UUID.randomUUID();
    entity.organizationId = organizationId;
    entity.code = code.toUpperCase().trim();
    entity.displayName = displayName.trim();
    entity.description = description;
    entity.ownerRole = ownerRole;
    entity.defaultRole = defaultRole;
    entity.createdAt = now;
    entity.updatedAt = now;
    return entity;
  }

  /** Seed mặc định khi tạo org mới */
  public static List<OrgCustomRoleEntity> seedDefaults(UUID organizationId, Instant now) {
    OrgCustomRoleEntity owner = create(organizationId, "OWNER", "Chủ doanh nghiệp",
        "Toàn quyền quản lý tổ chức", true, true, now);
    OrgCustomRoleEntity viewer = create(organizationId, "VIEWER", "Nhân viên",
        "Chỉ xem báo cáo và giao dịch", false, true, now);
    return List.of(owner, viewer);
  }

  public List<String> getPermissionCodes() {
    return permissions.stream()
        .map(OrgCustomRolePermissionEntity::getPermissionCode)
        .toList();
  }

  // --- Getters & Setters ---

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public UUID getOrganizationId() { return organizationId; }
  public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
  public String getCode() { return code; }
  public void setCode(String code) { this.code = code; }
  public String getDisplayName() { return displayName; }
  public void setDisplayName(String displayName) { this.displayName = displayName; }
  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }
  public boolean isOwnerRole() { return ownerRole; }
  public void setOwnerRole(boolean ownerRole) { this.ownerRole = ownerRole; }
  public boolean isDefaultRole() { return defaultRole; }
  public void setDefaultRole(boolean defaultRole) { this.defaultRole = defaultRole; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
  public Set<OrgCustomRolePermissionEntity> getPermissions() { return permissions; }
  public void setPermissions(Set<OrgCustomRolePermissionEntity> permissions) { this.permissions = permissions; }
}
