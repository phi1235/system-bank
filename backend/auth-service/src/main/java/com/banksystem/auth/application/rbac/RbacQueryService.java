package com.banksystem.auth.application.rbac;
import static com.banksystem.auth.api.dto.AuthDtos.*;
import static com.banksystem.auth.api.dto.PasswordResetDtos.*;
import static com.banksystem.auth.api.dto.RbacDtos.*;

import com.banksystem.common.api.PageResponse;
import com.banksystem.auth.api.dto.RbacDtos.*;
import com.banksystem.auth.api.dto.AuthDtos.StaffUserFilterRequest;
import com.banksystem.auth.domain.rbac.RoleEntity;
import java.util.List;
import java.util.UUID;

public interface RbacQueryService {
  MatrixResponse matrix();
  List<RoleDto> listRoles(boolean staffOnly);
  List<PermissionDto> listPermissions();
  PageResponse<StaffUserDto> listUsers(StaffUserFilterRequest req);
  PageResponse<StaffUserDto> listUsers(int page, int size, String q, Boolean enabled, String userId);
  StaffUserDto getUser(UUID userId);
  RoleDto getRole(String code);
  RoleEntity requireRole(String code);
}
