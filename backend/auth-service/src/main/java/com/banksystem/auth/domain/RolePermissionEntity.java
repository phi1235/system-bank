package com.banksystem.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "role_permissions")
@IdClass(RolePermissionEntity.Pk.class)
public class RolePermissionEntity {

  @Id
  @Column(name = "role_code", length = 40)
  private String roleCode;

  @Id
  @Column(name = "permission_code", length = 60)
  private String permissionCode;

  public String getRoleCode() {
    return roleCode;
  }

  public void setRoleCode(String roleCode) {
    this.roleCode = roleCode;
  }

  public String getPermissionCode() {
    return permissionCode;
  }

  public void setPermissionCode(String permissionCode) {
    this.permissionCode = permissionCode;
  }

  public static class Pk implements Serializable {
    private String roleCode;
    private String permissionCode;

    public Pk() {}

    public Pk(String roleCode, String permissionCode) {
      this.roleCode = roleCode;
      this.permissionCode = permissionCode;
    }

    public String getRoleCode() {
      return roleCode;
    }

    public void setRoleCode(String roleCode) {
      this.roleCode = roleCode;
    }

    public String getPermissionCode() {
      return permissionCode;
    }

    public void setPermissionCode(String permissionCode) {
      this.permissionCode = permissionCode;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Pk pk)) {
        return false;
      }
      return Objects.equals(roleCode, pk.roleCode)
          && Objects.equals(permissionCode, pk.permissionCode);
    }

    @Override
    public int hashCode() {
      return Objects.hash(roleCode, permissionCode);
    }
  }
}
