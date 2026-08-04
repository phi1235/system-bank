package com.banksystem.auth.application.rbac;
import static com.banksystem.auth.api.dto.AuthDtos.*;
import static com.banksystem.auth.api.dto.PasswordResetDtos.*;
import static com.banksystem.auth.api.dto.RbacDtos.*;

import com.banksystem.auth.api.dto.RbacDtos.*;
import java.util.UUID;

public interface RbacCommandService {
  RoleDto createRole(CreateRoleRequest req);
  RoleDto updateRole(String code, UpdateRoleRequest req);
  RoleDto updateRolePermissions(String code, UpdateRolePermissionsRequest req);
  StaffUserDto assignRoles(UUID actorId, UUID userId, AssignRolesRequest req);
}
