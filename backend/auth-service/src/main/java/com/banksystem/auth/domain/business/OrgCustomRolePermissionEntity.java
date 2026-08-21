package com.banksystem.auth.domain.business;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "org_custom_role_permissions")
@IdClass(OrgCustomRolePermissionEntity.Pk.class)
public class OrgCustomRolePermissionEntity {

  @Id
  @Column(name = "role_id", nullable = false)
  private UUID roleId;

  @Id
  @Column(name = "permission_code", nullable = false, length = 80)
  private String permissionCode;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "role_id", insertable = false, updatable = false)
  private OrgCustomRoleEntity role;


  public OrgCustomRolePermissionEntity() {}

  public OrgCustomRolePermissionEntity(OrgCustomRoleEntity role, String permissionCode) {
    this.role = role;
    this.roleId = role.getId();
    this.permissionCode = permissionCode;
  }

  public UUID getRoleId() { return roleId; }
  public OrgCustomRoleEntity getRole() { return role; }
  public void setRole(OrgCustomRoleEntity role) { this.role = role; }
  public String getPermissionCode() { return permissionCode; }
  public void setPermissionCode(String permissionCode) { this.permissionCode = permissionCode; }

  public static class Pk implements Serializable {
    private UUID roleId;
    private String permissionCode;

    public Pk() {}
    public Pk(UUID roleId, String permissionCode) {
      this.roleId = roleId;
      this.permissionCode = permissionCode;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Pk pk = (Pk) o;
      return Objects.equals(roleId, pk.roleId) && Objects.equals(permissionCode, pk.permissionCode);
    }

    @Override
    public int hashCode() {
      return Objects.hash(roleId, permissionCode);
    }
  }
}
