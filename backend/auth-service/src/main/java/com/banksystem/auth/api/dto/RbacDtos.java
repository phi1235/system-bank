package com.banksystem.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class RbacDtos {
  private RbacDtos() {}

  public record RoleDto(
      String code,
      String name,
      String description,
      boolean staff,
      List<String> permissions
  ) {}

  public record PermissionDto(String code, String description) {}

  public record MatrixCell(String roleCode, String permissionCode, boolean granted) {}

  public record MatrixResponse(
      List<RoleDto> roles,
      List<PermissionDto> permissions,
      List<MatrixCell> cells
  ) {}

  public record StaffUserDto(
      String userId,
      String username,
      String email,
      List<String> roles,
      List<String> permissions,
      boolean staff,
      boolean enabled,
      boolean mustChangePassword,
      String lockedReason,
      boolean openResetTicket
  ) {}

  public record AssignRolesRequest(@NotEmpty List<String> roles) {}

  public record CreateRoleRequest(
      @NotBlank
      @Size(min = 2, max = 40)
      @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]*$", message = "Role code: letters, digits, underscore")
      String code,
      @NotBlank @Size(max = 100) String name,
      @Size(max = 255) String description,
      @NotNull Boolean staff,
      List<String> permissions
  ) {}

  public record UpdateRoleRequest(
      @NotBlank @Size(max = 100) String name,
      @Size(max = 255) String description,
      Boolean staff
  ) {}

  public record UpdateRolePermissionsRequest(
      @NotNull List<String> permissions
  ) {}
}
