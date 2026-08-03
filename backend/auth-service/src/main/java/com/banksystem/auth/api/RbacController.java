package com.banksystem.auth.api;

import com.banksystem.auth.api.dto.RbacDtos.AssignRolesRequest;
import com.banksystem.auth.api.dto.RbacDtos.CreateRoleRequest;
import com.banksystem.auth.api.dto.RbacDtos.MatrixResponse;
import com.banksystem.auth.api.dto.RbacDtos.PermissionDto;
import com.banksystem.auth.api.dto.RbacDtos.RoleDto;
import com.banksystem.auth.api.dto.RbacDtos.StaffUserDto;
import com.banksystem.auth.api.dto.RbacDtos.UpdateRolePermissionsRequest;
import com.banksystem.auth.api.dto.RbacDtos.UpdateRoleRequest;
import com.banksystem.auth.application.RbacService;
import com.banksystem.auth.config.UserPrincipal;
import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.api.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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

  private final RbacService rbacService;

  public RbacController(RbacService rbacService) {
    this.rbacService = rbacService;
  }

  @GetMapping("/matrix")
  public ApiResponse<MatrixResponse> matrix(@AuthenticationPrincipal UserPrincipal principal) {
    RbacService.requireRbacAccess(principal);
    return ApiResponse.ok(rbacService.matrix());
  }

  @GetMapping("/roles")
  public ApiResponse<List<RoleDto>> roles(
      @AuthenticationPrincipal UserPrincipal principal,
      @RequestParam(defaultValue = "true") boolean staffOnly) {
    RbacService.requireRbacAccess(principal);
    return ApiResponse.ok(rbacService.listRoles(staffOnly));
  }

  @GetMapping("/roles/{code}")
  public ApiResponse<RoleDto> role(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable String code) {
    RbacService.requireRolesManage(principal);
    return ApiResponse.ok(rbacService.getRole(code));
  }

  @PostMapping("/roles")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<RoleDto> createRole(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody CreateRoleRequest body) {
    RbacService.requireRolesManage(principal);
    return ApiResponse.ok(rbacService.createRole(body));
  }

  @PutMapping("/roles/{code}")
  public ApiResponse<RoleDto> updateRole(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable String code,
      @Valid @RequestBody UpdateRoleRequest body) {
    RbacService.requireRolesManage(principal);
    return ApiResponse.ok(rbacService.updateRole(code, body));
  }

  @PutMapping("/roles/{code}/permissions")
  public ApiResponse<RoleDto> updateRolePermissions(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable String code,
      @Valid @RequestBody UpdateRolePermissionsRequest body) {
    RbacService.requireRolesManage(principal);
    return ApiResponse.ok(rbacService.updateRolePermissions(code, body));
  }

  @GetMapping("/permissions")
  public ApiResponse<List<PermissionDto>> permissions(@AuthenticationPrincipal UserPrincipal principal) {
    RbacService.requireRbacAccess(principal);
    return ApiResponse.ok(rbacService.listPermissions());
  }

  @GetMapping("/users")
  public ApiResponse<PageResponse<StaffUserDto>> users(
      @AuthenticationPrincipal UserPrincipal principal,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Boolean enabled,
      @RequestParam(required = false) String userId) {
    RbacService.requireUsersAssign(principal);
    return ApiResponse.ok(rbacService.listUsers(page, size, q, enabled, userId));
  }

  @GetMapping("/users/{userId}")
  public ApiResponse<StaffUserDto> user(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID userId) {
    RbacService.requireUsersAssign(principal);
    return ApiResponse.ok(rbacService.getUser(userId));
  }

  @PutMapping("/users/{userId}/roles")
  public ApiResponse<StaffUserDto> assignRoles(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable UUID userId,
      @Valid @RequestBody AssignRolesRequest body) {
    RbacService.requireUsersAssign(principal);
    return ApiResponse.ok(rbacService.assignRoles(principal.userId(), userId, body));
  }
}
