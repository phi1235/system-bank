package com.banksystem.auth.api.rbac;
import static com.banksystem.auth.api.dto.AuthDtos.*;
import static com.banksystem.auth.api.dto.PasswordResetDtos.*;
import static com.banksystem.auth.api.dto.RbacDtos.*;
import com.banksystem.auth.application.auth.*;
import com.banksystem.auth.application.rbac.*;
import com.banksystem.auth.domain.auth.*;
import com.banksystem.auth.domain.rbac.*;
import com.banksystem.auth.api.dto.*;

import com.banksystem.auth.api.dto.AuthDtos.StaffUserFilterRequest;
import com.banksystem.auth.api.dto.RbacDtos.AssignRolesRequest;
import com.banksystem.auth.api.dto.RbacDtos.CreateRoleRequest;
import com.banksystem.auth.api.dto.RbacDtos.MatrixResponse;
import com.banksystem.auth.api.dto.RbacDtos.PermissionDto;
import com.banksystem.auth.api.dto.RbacDtos.RoleDto;
import com.banksystem.auth.api.dto.RbacDtos.StaffUserDto;
import com.banksystem.auth.api.dto.RbacDtos.UpdateRolePermissionsRequest;
import com.banksystem.auth.api.dto.RbacDtos.UpdateRoleRequest;
import com.banksystem.auth.config.UserPrincipal;
import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.api.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/rbac")
public class RbacController {

  private final RbacQueryService queryService;
  private final RbacCommandService commandService;

  public RbacController(RbacQueryService queryService, RbacCommandService commandService) {
    this.queryService = queryService;
    this.commandService = commandService;
  }

  @GetMapping("/matrix")
  public ApiResponse<MatrixResponse> matrix(@AuthenticationPrincipal UserPrincipal principal) {
    PermissionChecker.requireRbacAccess(principal);
    return ApiResponse.ok(queryService.matrix());
  }

  @GetMapping("/roles")
  public ApiResponse<List<RoleDto>> roles(
      @AuthenticationPrincipal UserPrincipal principal,
      @RequestParam(defaultValue = "true") boolean staffOnly) {
    PermissionChecker.requireRbacAccess(principal);
    return ApiResponse.ok(queryService.listRoles(staffOnly));
  }

  @GetMapping("/roles/{code}")
  public ApiResponse<RoleDto> role(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable String code) {
    PermissionChecker.requireRolesManage(principal);
    return ApiResponse.ok(queryService.getRole(code));
  }

  @PostMapping("/roles")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<RoleDto> createRole(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody CreateRoleRequest body) {
    PermissionChecker.requireRolesManage(principal);
    return ApiResponse.ok(commandService.createRole(body));
  }

  @PutMapping("/roles/{code}")
  public ApiResponse<RoleDto> updateRole(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable String code,
      @Valid @RequestBody UpdateRoleRequest body) {
    PermissionChecker.requireRolesManage(principal);
    return ApiResponse.ok(commandService.updateRole(code, body));
  }

  @PutMapping("/roles/{code}/permissions")
  public ApiResponse<RoleDto> updateRolePermissions(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable String code,
      @Valid @RequestBody UpdateRolePermissionsRequest body) {
    PermissionChecker.requireRolesManage(principal);
    return ApiResponse.ok(commandService.updateRolePermissions(code, body));
  }

  @GetMapping("/permissions")
  public ApiResponse<List<PermissionDto>> permissions(@AuthenticationPrincipal UserPrincipal principal) {
    PermissionChecker.requireRbacAccess(principal);
    return ApiResponse.ok(queryService.listPermissions());
  }

  @GetMapping("/users")
  public ApiResponse<PageResponse<StaffUserDto>> users(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @ModelAttribute StaffUserFilterRequest req) {
    PermissionChecker.requireUsersAssign(principal);
    return ApiResponse.ok(queryService.listUsers(req));
  }

  @PostMapping("/users/findUserByCondition")
  public ApiResponse<PageResponse<StaffUserDto>> findUserByCondition(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody StaffUserFilterRequest req) {
    PermissionChecker.requireUsersAssign(principal);
    return ApiResponse.ok(queryService.listUsers(req));
  }

  @GetMapping("/users/{userId}")
  public ApiResponse<StaffUserDto> user(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID userId) {
    PermissionChecker.requireUsersAssign(principal);
    return ApiResponse.ok(queryService.getUser(userId));
  }

  @PutMapping("/users/{userId}/roles")
  public ApiResponse<StaffUserDto> assignRoles(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID userId,
      @Valid @RequestBody AssignRolesRequest body) {
    PermissionChecker.requireUsersAssign(principal);
    return ApiResponse.ok(commandService.assignRoles(principal.userId(), userId, body));
  }
}
