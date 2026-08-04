package com.banksystem.auth.application.rbac.impl;
import static com.banksystem.auth.api.dto.AuthDtos.*;
import static com.banksystem.auth.api.dto.PasswordResetDtos.*;
import static com.banksystem.auth.api.dto.RbacDtos.*;
import com.banksystem.auth.application.auth.*;
import com.banksystem.auth.application.rbac.*;
import com.banksystem.auth.domain.auth.*;
import com.banksystem.auth.domain.rbac.*;
import com.banksystem.auth.api.dto.*;

import com.banksystem.auth.api.dto.RbacDtos.AssignRolesRequest;
import com.banksystem.auth.api.dto.RbacDtos.CreateRoleRequest;
import com.banksystem.auth.api.dto.RbacDtos.RoleDto;
import com.banksystem.auth.api.dto.RbacDtos.StaffUserDto;
import com.banksystem.auth.api.dto.RbacDtos.UpdateRolePermissionsRequest;
import com.banksystem.auth.api.dto.RbacDtos.UpdateRoleRequest;
import com.banksystem.auth.application.mapper.RbacMapper;
import com.banksystem.common.exception.BusinessException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RbacCommandServiceImpl implements RbacCommandService {

  private static final Set<String> FULL_ADMIN_ROLES = Set.of("ADMIN", "SUPER_ADMIN");

  private final RoleRepository roleRepository;
  private final PermissionRepository permissionRepository;
  private final RolePermissionRepository rolePermissionRepository;
  private final UserRepository userRepository;
  private final PasswordResetTicketRepository passwordResetTicketRepository;
  private final RbacMapper mapper;

  public RbacCommandServiceImpl(
      RoleRepository roleRepository,
      PermissionRepository permissionRepository,
      RolePermissionRepository rolePermissionRepository,
      UserRepository userRepository,
      PasswordResetTicketRepository passwordResetTicketRepository,
      RbacMapper mapper) {
    this.roleRepository = roleRepository;
    this.permissionRepository = permissionRepository;
    this.rolePermissionRepository = rolePermissionRepository;
    this.userRepository = userRepository;
    this.passwordResetTicketRepository = passwordResetTicketRepository;
    this.mapper = mapper;
  }

  @Transactional
  public RoleDto createRole(CreateRoleRequest req) {
    String code = req.code().trim().toUpperCase(Locale.ROOT);
    if (roleRepository.existsById(code)) {
      throw new BusinessException("ROLE_EXISTS", "Role already exists: " + code);
    }
    RoleEntity role = new RoleEntity();
    role.setCode(code);
    role.setName(req.name().trim());
    role.setDescription(req.description() == null ? "" : req.description().trim());
    role.setStaff(req.staff() == null || req.staff());
    roleRepository.save(role);
    replacePermissions(code, req.permissions());
    return mapper.toRoleDto(role);
  }

  @Transactional
  public RoleDto updateRole(String code, UpdateRoleRequest req) {
    RoleEntity role = requireRole(code);
    role.setName(req.name().trim());
    role.setDescription(req.description() == null ? "" : req.description().trim());
    if (req.staff() != null && !"CUSTOMER".equals(role.getCode())) {
      role.setStaff(req.staff());
    }
    roleRepository.save(role);
    return mapper.toRoleDto(role);
  }

  @Transactional
  public RoleDto updateRolePermissions(String code, UpdateRolePermissionsRequest req) {
    RoleEntity role = requireRole(code);
    replacePermissions(role.getCode(), req.permissions());
    return mapper.toRoleDto(role);
  }

  @Transactional
  public StaffUserDto assignRoles(UUID actorId, UUID userId, AssignRolesRequest req) {
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found"));

    List<String> nextRoles = req.roles() == null ? List.of() : req.roles().stream()
        .map(r -> r == null ? "" : r.trim().toUpperCase(Locale.ROOT))
        .filter(s -> !s.isEmpty())
        .map(r -> r.startsWith("ROLE_") ? r.substring(5) : r)
        .distinct()
        .toList();

    if (nextRoles.isEmpty()) {
      throw new BusinessException("INVALID_ROLES", "At least one role is required");
    }

    Set<String> known = roleRepository.findAllById(nextRoles).stream()
        .map(RoleEntity::getCode)
        .collect(Collectors.toSet());
    for (String role : nextRoles) {
      if (!known.contains(role)) {
        throw new BusinessException("UNKNOWN_ROLE", "Unknown role: " + role);
      }
    }

    boolean demotingFullAdmin = isFullAdmin(user.roleList()) && !nextRoles.stream().anyMatch(FULL_ADMIN_ROLES::contains);
    if (demotingFullAdmin && countFullAdmins() <= 1) {
      throw new BusinessException("LAST_ADMIN", "Cannot remove the last ADMIN / SUPER_ADMIN");
    }

    user.setRoles(String.join(",", nextRoles));
    user.setUpdatedAt(Instant.now());
    userRepository.save(user);
    boolean openTicket = passwordResetTicketRepository.existsByUserIdAndStatus(user.getId(), "OPEN");
    return mapper.toStaffDto(user, openTicket);
  }

  private RoleEntity requireRole(String code) {
    String c = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    return roleRepository.findById(c)
        .orElseThrow(() -> new BusinessException("ROLE_NOT_FOUND", "Role not found: " + c));
  }

  private void replacePermissions(String roleCode, List<String> permissions) {
    List<String> next = permissions == null ? List.of() : permissions.stream()
        .map(p -> p == null ? "" : p.trim())
        .filter(s -> !s.isEmpty())
        .distinct()
        .toList();

    Set<String> known = permissionRepository.findAllById(next).stream()
        .map(PermissionEntity::getCode)
        .collect(Collectors.toSet());
    for (String p : next) {
      if (!known.contains(p)) {
        if (!p.matches("^[a-z][a-z0-9_]*:[a-z0-9_.:-]+$")) {
          throw new BusinessException("UNKNOWN_PERMISSION", "Invalid permission code: " + p);
        }
        PermissionEntity pe = new PermissionEntity();
        pe.setCode(p);
        pe.setDescription(p);
        permissionRepository.save(pe);
        known.add(p);
      }
    }

    rolePermissionRepository.deleteByRoleCode(roleCode);
    rolePermissionRepository.flush();

    for (String p : next) {
      RolePermissionEntity link = new RolePermissionEntity();
      link.setRoleCode(roleCode);
      link.setPermissionCode(p);
      rolePermissionRepository.save(link);
    }
  }

  private long countFullAdmins() {
    return userRepository.countByAnyRole(FULL_ADMIN_ROLES);
  }

  private boolean isFullAdmin(List<String> roles) {
    return roles.stream().anyMatch(r -> FULL_ADMIN_ROLES.contains(r.toUpperCase(Locale.ROOT)));
  }
}
