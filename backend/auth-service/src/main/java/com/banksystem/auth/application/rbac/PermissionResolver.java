package com.banksystem.auth.application.rbac;

import com.banksystem.auth.domain.auth.UserEntity;
import com.banksystem.auth.domain.business.BusinessMemberEntity;
import com.banksystem.auth.domain.business.BusinessMemberRepository;
import com.banksystem.auth.domain.rbac.RolePermissionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PermissionResolver {

  private final RolePermissionRepository rolePermissionRepository;
  private final BusinessMemberRepository businessMemberRepository;

  public PermissionResolver(
      RolePermissionRepository rolePermissionRepository,
      BusinessMemberRepository businessMemberRepository) {
    this.rolePermissionRepository = rolePermissionRepository;
    this.businessMemberRepository = businessMemberRepository;
  }

  @Transactional(readOnly = true)
  public List<String> resolvePermissions(UserEntity user) {
    if (user == null) {
      return List.of();
    }
    List<String> combinedRoles = new ArrayList<>(user.roleList());
    if (user.getId() != null) {
      List<String> businessRoles = businessMemberRepository.findByUserId(user.getId()).stream()
          .filter(m -> "ACTIVE".equalsIgnoreCase(m.getStatus()))
          .map(BusinessMemberEntity::getBusinessRole)
          .toList();
      combinedRoles.addAll(businessRoles);
    }
    return resolvePermissions(combinedRoles);
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
}
