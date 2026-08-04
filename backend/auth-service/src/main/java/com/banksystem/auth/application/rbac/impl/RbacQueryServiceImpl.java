package com.banksystem.auth.application.rbac.impl;
import static com.banksystem.auth.api.dto.AuthDtos.*;
import static com.banksystem.auth.api.dto.PasswordResetDtos.*;
import static com.banksystem.auth.api.dto.RbacDtos.*;
import com.banksystem.auth.application.auth.*;
import com.banksystem.auth.application.rbac.*;
import com.banksystem.auth.domain.auth.*;
import com.banksystem.auth.domain.rbac.*;
import com.banksystem.auth.api.dto.*;

import com.banksystem.auth.api.dto.AuthDtos.StaffUserFilterRequest;
import com.banksystem.auth.api.dto.RbacDtos.MatrixCell;
import com.banksystem.auth.api.dto.RbacDtos.MatrixResponse;
import com.banksystem.auth.api.dto.RbacDtos.PermissionDto;
import com.banksystem.auth.api.dto.RbacDtos.RoleDto;
import com.banksystem.auth.api.dto.RbacDtos.StaffUserDto;
import com.banksystem.auth.application.mapper.RbacMapper;
import com.banksystem.common.api.PageResponse;
import com.banksystem.common.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RbacQueryServiceImpl implements RbacQueryService {

  private final RoleRepository roleRepository;
  private final PermissionRepository permissionRepository;
  private final RolePermissionRepository rolePermissionRepository;
  private final UserRepository userRepository;
  private final PasswordResetTicketRepository passwordResetTicketRepository;
  private final RbacMapper mapper;

  public RbacQueryServiceImpl(
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
        roles.stream().map(mapper::toRoleDto).toList(),
        permissions.stream().map(mapper::toPermDto).toList(),
        cells
    );
  }

  @Transactional(readOnly = true)
  public List<RoleDto> listRoles(boolean staffOnly) {
    List<RoleEntity> roles = staffOnly
        ? roleRepository.findByStaffTrueOrderByCodeAsc()
        : roleRepository.findAllByOrderByCodeAsc();
    return roles.stream().map(mapper::toRoleDto).toList();
  }

  @Transactional(readOnly = true)
  public List<PermissionDto> listPermissions() {
    return permissionRepository.findAllByOrderByCodeAsc().stream().map(mapper::toPermDto).toList();
  }

  @Transactional(readOnly = true)
  public PageResponse<StaffUserDto> listUsers(StaffUserFilterRequest req) {
    int page = req.page() == null || req.page() < 0 ? 0 : req.page();
    int size = req.size() == null || req.size() < 1 ? 20 : req.size();
    return listUsers(page, size, req.q(), req.enabled(), req.userId());
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
        throw new BusinessException("INVALID_USER_ID", "userId must be a valid UUID");
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
    List<StaffUserDto> items = result.getContent().stream().map(u -> {
      boolean openTicket = passwordResetTicketRepository.existsByUserIdAndStatus(u.getId(), "OPEN");
      return mapper.toStaffDto(u, openTicket);
    }).toList();

    return new PageResponse<>(
        items,
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages());
  }

  @Transactional(readOnly = true)
  public StaffUserDto getUser(UUID userId) {
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found"));
    boolean openTicket = passwordResetTicketRepository.existsByUserIdAndStatus(user.getId(), "OPEN");
    return mapper.toStaffDto(user, openTicket);
  }

  @Transactional(readOnly = true)
  public RoleDto getRole(String code) {
    return mapper.toRoleDto(requireRole(code));
  }

  public RoleEntity requireRole(String code) {
    String c = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    return roleRepository.findById(c)
        .orElseThrow(() -> new BusinessException("ROLE_NOT_FOUND", "Role not found: " + c));
  }
}
