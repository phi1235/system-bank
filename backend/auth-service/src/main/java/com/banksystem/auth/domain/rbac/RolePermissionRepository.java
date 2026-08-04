package com.banksystem.auth.domain.rbac;
import static com.banksystem.auth.api.dto.AuthDtos.*;
import static com.banksystem.auth.api.dto.PasswordResetDtos.*;
import static com.banksystem.auth.api.dto.RbacDtos.*;
import com.banksystem.auth.application.auth.*;
import com.banksystem.auth.application.rbac.*;
import com.banksystem.auth.domain.auth.*;
import com.banksystem.auth.domain.rbac.*;
import com.banksystem.auth.api.dto.*;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RolePermissionRepository extends JpaRepository<RolePermissionEntity, RolePermissionEntity.Pk> {

  List<RolePermissionEntity> findByRoleCodeIn(Collection<String> roleCodes);

  List<RolePermissionEntity> findByRoleCode(String roleCode);

  void deleteByRoleCode(String roleCode);

  @Query("select distinct rp.permissionCode from RolePermissionEntity rp where rp.roleCode in :roles")
  List<String> findPermissionCodesByRoleCodes(@Param("roles") Collection<String> roles);

  List<RolePermissionEntity> findAll();
}
