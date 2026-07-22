package com.banksystem.auth.application;

import com.banksystem.auth.api.dto.RbacDtos.AssignRolesRequest;
import com.banksystem.auth.api.dto.RbacDtos.CreateRoleRequest;
import com.banksystem.auth.api.dto.RbacDtos.MatrixCell;
import com.banksystem.auth.api.dto.RbacDtos.MatrixResponse;
import com.banksystem.auth.api.dto.RbacDtos.PermissionDto;
import com.banksystem.auth.api.dto.RbacDtos.RoleDto;
import com.banksystem.auth.api.dto.RbacDtos.StaffUserDto;
import com.banksystem.auth.api.dto.RbacDtos.UpdateRolePermissionsRequest;
import com.banksystem.auth.api.dto.RbacDtos.UpdateRoleRequest;
import com.banksystem.auth.domain.PasswordResetTicketRepository;
import com.banksystem.auth.domain.PermissionEntity;
import com.banksystem.auth.domain.PermissionRepository;
import com.banksystem.auth.domain.RoleEntity;
import com.banksystem.auth.domain.RolePermissionEntity;
import com.banksystem.auth.domain.RolePermissionRepository;
import com.banksystem.auth.domain.RoleRepository;
import com.banksystem.auth.domain.UserEntity;
import com.banksystem.auth.domain.UserRepository;
import com.banksystem.common.api.PageResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.common.security.SecurityHeaders;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RbacService {

  private static final Set<String> FULL_ADMIN_ROLES = Set.of("ADMIN", "SUPER_ADMIN");

  private final RoleRepository roleRepository;
  private final PermissionRepository permissionRepository;
  private final RolePermissionRepository rolePermissionRepository;
  private final UserRepository userRepository;
  private final PasswordResetTicketRepository passwordResetTicketRepository;

  public RbacService(
      RoleRepository roleRepository,
      PermissionRepository permissionRepository,
      RolePermissionRepository rolePermissionRepository,
      UserRepository userRepository,
      PasswordResetTicketRepository passwordResetTicketRepository) {
    this.roleRepository = roleRepository;
    this.permissionRepository = permissionRepository;
    this.rolePermissionRepository = rolePermissionRepository;
    this.userRepository = userRepository;
    this.passwordResetTicketRepository = passwordResetTicketRepository;
  }

  @Transactional(readOnly = true)
  public List<String> resolvePermissions(List<String> roles) {
    if (roles == null || roles.isEmpty()) {
      return List.of();
    }
    List<String> normalized = roles.stream()
        .map(r -> r == null ? "" : r.trim().toUpperCase(Locale.ROOT))
        .filter(s -> !s.isEmpty())
        .map(r -> r.startsWith("ROLE_") ? r.substring(5) : r)
        .distinct()
        .toList();
    if (normalized.isEmpty()) {
      return List.of();
    }
    List<String> perms = rolePermissionRepository.findPermissionCodesByRoleCodes(normalized);
    return perms.stream().sorted().distinct().toList();
  }

  @Transactional(readOnly = true)
  public List<String> resolvePermissions(UserEntity user) {
    return resolvePermissions(user.roleList());
  }

  public boolean isStaff(List<String> roles) {
    if (roles == null) {
      return false;
    }
    return roles.stream().anyMatch(r -> {
      String n = r == null ? "" : r.trim().toUpperCase(Locale.ROOT);
      if (n.startsWith("ROLE_")) {
        n = n.substring(5);
      }
      return !n.isEmpty() && !"CUSTOMER".equals(n);
    });
  }

  @Transactional(readOnly = true)
  public MatrixResponse matrix() {
    List<RoleEntity> roles = roleRepository.findAllByOrderByCodeAsc().stream()
        .filter(RoleEntity::isStaff)
        .toList();
    List<PermissionEntity> permissions = permissionRepository.findAllByOrderByCodeAsc();
    List<RolePermissionEntity> links = rolePermissionRepository.findAll();
    Set<String> granted = links.stream()
        .map(rp -> rp.getRoleCode() + "|" + rp.getPermissionCode())
        .collect(Collectors.toSet());

    List<MatrixCell> cells = new ArrayList<>();
    for (RoleEntity role : roles) {
      for (PermissionEntity perm : permissions) {
        boolean on = granted.contains(role.getCode() + "|" + perm.getCode());
        cells.add(new MatrixCell(role.getCode(), perm.getCode(), on));
      }
    }

    return new MatrixResponse(
        roles.stream().map(this::toRoleDto).toList(),
        permissions.stream().map(this::toPermDto).toList(),
        cells
    );
  }

  @Transactional(readOnly = true)
  public List<RoleDto> listRoles(boolean staffOnly) {
    List<RoleEntity> roles = staffOnly
        ? roleRepository.findByStaffTrueOrderByCodeAsc()
        : roleRepository.findAllByOrderByCodeAsc();
    return roles.stream().map(this::toRoleDto).toList();
  }

  @Transactional(readOnly = true)
  public List<PermissionDto> listPermissions() {
    return permissionRepository.findAllByOrderByCodeAsc().stream().map(this::toPermDto).toList();
  }

  @Transactional(readOnly = true)
  public PageResponse<StaffUserDto> listUsers(int page, int size, String q, Boolean enabled, String userId) {
    PageRequest pr = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    String qNorm = q == null ? null : q.trim();
    boolean hasQ = qNorm != null && !qNorm.isEmpty();
    boolean hasEnabled = enabled != null;
    UUID uid = null;
    boolean hasUserId = false;
    if (userId != null && !userId.isBlank()) {
      try {
        uid = UUID.fromString(userId.trim());
        hasUserId = true;
      } catch (IllegalArgumentException ex) {
        throw new BusinessException(
            "INVALID_USER_ID", "userId must be a valid UUID", HttpStatus.BAD_REQUEST);
      }
    }
    Page<UserEntity> result =
        userRepository.searchAdmin(
            hasUserId,
            hasUserId ? uid : new UUID(0L, 0L),
            hasEnabled,
            hasEnabled && enabled,
            hasQ,
            hasQ ? qNorm : "",
            pr);
    List<StaffUserDto> items = result.getContent().stream().map(this::toStaffDto).toList();
    return new PageResponse<>(
        items,
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages());
  }

  @Transactional(readOnly = true)
  public StaffUserDto getUser(UUID userId) {
    UserEntity user =
        userRepository
            .findById(userId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        "USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND));
    return toStaffDto(user);
  }

  @Transactional
  public RoleDto createRole(CreateRoleRequest req) {
    String code = req.code().trim().toUpperCase(Locale.ROOT);
    if (roleRepository.existsById(code)) {
      throw new BusinessException("ROLE_EXISTS", "Role already exists: " + code, HttpStatus.CONFLICT);
    }
    RoleEntity role = new RoleEntity();
    role.setCode(code);
    role.setName(req.name().trim());
    role.setDescription(req.description() == null ? "" : req.description().trim());
    role.setStaff(req.staff() == null || req.staff());
    roleRepository.save(role);
    replacePermissions(code, req.permissions());
    return toRoleDto(role);
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
    return toRoleDto(role);
  }

  /**
   * Replace the permission set of a role. All users holding this role receive the new
   * effective permissions on their next login (JWT re-issued from role_permissions).
   */
  @Transactional
  public RoleDto updateRolePermissions(String code, UpdateRolePermissionsRequest req) {
    RoleEntity role = requireRole(code);
    replacePermissions(role.getCode(), req.permissions());
    return toRoleDto(role);
  }

  @Transactional(readOnly = true)
  public RoleDto getRole(String code) {
    return toRoleDto(requireRole(code));
  }

  private RoleEntity requireRole(String code) {
    String c = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    return roleRepository.findById(c)
        .orElseThrow(() -> new BusinessException("ROLE_NOT_FOUND", "Role not found: " + c, HttpStatus.NOT_FOUND));
  }

  private void replacePermissions(String roleCode, List<String> permissions) {
    List<String> next = permissions == null ? List.of() : permissions.stream()
        .map(p -> p == null ? "" : p.trim())
        .filter(s -> !s.isEmpty())
        .distinct()
        .toList();

    // Ensure catalog rows exist (auto-register unknown codes so UI catalog stays in sync)
    Set<String> known = permissionRepository.findAll().stream()
        .map(PermissionEntity::getCode)
        .collect(Collectors.toSet());
    for (String p : next) {
      if (!known.contains(p)) {
        if (!p.matches("^[a-z][a-z0-9_]*:[a-z0-9_.:-]+$")) {
          throw new BusinessException("UNKNOWN_PERMISSION", "Invalid permission code: " + p,
              HttpStatus.BAD_REQUEST);
        }
        PermissionEntity pe = new PermissionEntity();
        pe.setCode(p);
        pe.setDescription(p);
        permissionRepository.save(pe);
        known.add(p);
      }
    }

    rolePermissionRepository.deleteByRoleCode(roleCode);
    // flush delete before re-insert same PK
    rolePermissionRepository.flush();

    for (String p : next) {
      RolePermissionEntity link = new RolePermissionEntity();
      link.setRoleCode(roleCode);
      link.setPermissionCode(p);
      rolePermissionRepository.save(link);
    }
  }

  @Transactional
  public StaffUserDto assignRoles(UUID actorId, UUID userId, AssignRolesRequest req) {
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND));

    List<String> nextRoles = req.roles() == null ? List.of() : req.roles().stream()
        .map(r -> r == null ? "" : r.trim().toUpperCase(Locale.ROOT))
        .filter(s -> !s.isEmpty())
        .map(r -> r.startsWith("ROLE_") ? r.substring(5) : r)
        .distinct()
        .toList();

    if (nextRoles.isEmpty()) {
      throw new BusinessException("INVALID_ROLES", "At least one role is required", HttpStatus.BAD_REQUEST);
    }

    Set<String> known = roleRepository.findAll().stream().map(RoleEntity::getCode).collect(Collectors.toSet());
    for (String role : nextRoles) {
      if (!known.contains(role)) {
        throw new BusinessException("UNKNOWN_ROLE", "Unknown role: " + role, HttpStatus.BAD_REQUEST);
      }
    }

    boolean demotingFullAdmin = isFullAdmin(user.roleList()) && !nextRoles.stream().anyMatch(FULL_ADMIN_ROLES::contains);
    if (demotingFullAdmin && countFullAdmins() <= 1) {
      throw new BusinessException("LAST_ADMIN", "Cannot remove the last ADMIN / SUPER_ADMIN",
          HttpStatus.CONFLICT);
    }

    if (userId.equals(actorId) && demotingFullAdmin) {
      // still allow if other admins exist (checked above)
    }

    user.setRoles(String.join(",", nextRoles));
    user.setUpdatedAt(Instant.now());
    userRepository.save(user);
    return toStaffDto(user);
  }

  private long countFullAdmins() {
    return userRepository.findAll().stream()
        .filter(u -> isFullAdmin(u.roleList()))
        .count();
  }

  private boolean isFullAdmin(List<String> roles) {
    return roles.stream().anyMatch(r -> FULL_ADMIN_ROLES.contains(r.toUpperCase(Locale.ROOT)));
  }

  private StaffUserDto toStaffDto(UserEntity u) {
    List<String> roles = u.roleList();
    boolean openTicket = passwordResetTicketRepository.existsByUserIdAndStatus(u.getId(), "OPEN");
    return new StaffUserDto(
        u.getId().toString(),
        u.getUsername(),
        u.getEmail(),
        roles,
        resolvePermissions(roles),
        isStaff(roles),
        u.isEnabled(),
        u.isMustChangePassword(),
        u.getLockedReason(),
        openTicket,
        u.getCreatedAt());
  }

  private RoleDto toRoleDto(RoleEntity r) {
    List<String> perms = rolePermissionRepository.findPermissionCodesByRoleCodes(List.of(r.getCode()));
    return new RoleDto(r.getCode(), r.getName(), r.getDescription(), r.isStaff(), perms);
  }

  private PermissionDto toPermDto(PermissionEntity p) {
    return new PermissionDto(p.getCode(), p.getDescription());
  }

  /** Helper for permission checks inside auth-service controllers. */
  public static void requirePermission(List<String> permissions, String required) {
    if (permissions != null && permissions.stream().anyMatch(p -> p.equalsIgnoreCase(required) || "*".equals(p))) {
      return;
    }
    throw new BusinessException("FORBIDDEN", "Missing permission: " + required, HttpStatus.FORBIDDEN);
  }

  public static List<String> parseCsv(String csv) {
    if (csv == null || csv.isBlank()) {
      return List.of();
    }
    return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
  }

  public static final List<String> ALL_PERMISSIONS = List.of(
      SecurityHeaders.PERM_DASHBOARD_VIEW,
      SecurityHeaders.PERM_CUSTOMERS_LIST_VIEW,
      SecurityHeaders.PERM_CUSTOMERS_KYC_DECIDE,
      SecurityHeaders.PERM_ACCOUNTS_LOOKUP_VIEW,
      SecurityHeaders.PERM_ACCOUNTS_FREEZE_EXECUTE,
      SecurityHeaders.PERM_TX_LIST_VIEW,
      SecurityHeaders.PERM_AUDIT_LIST_VIEW,
      SecurityHeaders.PERM_RBAC_ACCESS,
      SecurityHeaders.PERM_RBAC_USERS_ASSIGN,
      SecurityHeaders.PERM_RBAC_ROLES_MANAGE,
      SecurityHeaders.PERM_RISK_VIEW,
      SecurityHeaders.PERM_IB_HOME_VIEW,
      SecurityHeaders.PERM_IB_ACCOUNTS_VIEW,
      SecurityHeaders.PERM_IB_ACCOUNTS_OPEN,
      SecurityHeaders.PERM_IB_TRANSFER_VIEW,
      SecurityHeaders.PERM_IB_TRANSFER_EXECUTE,
      SecurityHeaders.PERM_IB_HISTORY_VIEW,
      SecurityHeaders.PERM_IB_PROFILE_VIEW,
      SecurityHeaders.PERM_IB_PROFILE_EDIT,
      SecurityHeaders.PERM_IB_PROFILE_MFA,
      SecurityHeaders.PERM_IB_CARDS_VIEW,
      SecurityHeaders.PERM_IB_WEALTH_VIEW,
      SecurityHeaders.PERM_IB_SUPPORT_VIEW
  );

  public static boolean hasAny(List<String> permissions, String... required) {
    if (permissions == null) {
      return false;
    }
    for (String r : required) {
      if (permissions.stream().anyMatch(p -> p.equalsIgnoreCase(r) || "*".equals(p))) {
        return true;
      }
    }
    return false;
  }
}
