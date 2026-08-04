package com.banksystem.auth.application.mapper;
import static com.banksystem.auth.api.dto.AuthDtos.*;
import static com.banksystem.auth.api.dto.PasswordResetDtos.*;
import static com.banksystem.auth.api.dto.RbacDtos.*;
import com.banksystem.auth.application.auth.*;
import com.banksystem.auth.application.rbac.*;
import com.banksystem.auth.domain.auth.*;
import com.banksystem.auth.domain.rbac.*;
import com.banksystem.auth.api.dto.*;

import com.banksystem.auth.api.dto.RbacDtos.PermissionDto;
import com.banksystem.auth.api.dto.RbacDtos.RoleDto;
import com.banksystem.auth.api.dto.RbacDtos.StaffUserDto;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RbacMapper {

  private final RolePermissionRepository rolePermissionRepository;
  private final PermissionResolver permissionResolver;

  public RbacMapper(
      RolePermissionRepository rolePermissionRepository,
      PermissionResolver permissionResolver) {
    this.rolePermissionRepository = rolePermissionRepository;
    this.permissionResolver = permissionResolver;
  }

  public StaffUserDto toStaffDto(UserEntity u, boolean openTicket) {
    List<String> roles = u.roleList();
    return new StaffUserDto(
        u.getId().toString(),
        u.getUsername(),
        u.getEmail(),
        roles,
        permissionResolver.resolvePermissions(roles),
        permissionResolver.isStaff(roles),
        u.isEnabled(),
        u.isMustChangePassword(),
        u.getLockedReason(),
        openTicket,
        u.getCreatedAt());
  }

  public RoleDto toRoleDto(RoleEntity r) {
    List<String> perms = rolePermissionRepository.findPermissionCodesByRoleCodes(List.of(r.getCode()));
    return new RoleDto(r.getCode(), r.getName(), r.getDescription(), r.isStaff(), perms);
  }

  public PermissionDto toPermDto(PermissionEntity p) {
    return new PermissionDto(p.getCode(), p.getDescription());
  }
}
