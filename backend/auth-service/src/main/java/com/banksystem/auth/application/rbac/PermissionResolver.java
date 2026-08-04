package com.banksystem.auth.application.rbac;
import static com.banksystem.auth.api.dto.AuthDtos.*;
import static com.banksystem.auth.api.dto.PasswordResetDtos.*;
import static com.banksystem.auth.api.dto.RbacDtos.*;
import com.banksystem.auth.application.auth.*;
import com.banksystem.auth.application.rbac.*;
import com.banksystem.auth.domain.auth.*;
import com.banksystem.auth.domain.rbac.*;
import com.banksystem.auth.api.dto.*;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PermissionResolver {

  private final RolePermissionRepository rolePermissionRepository;

  public PermissionResolver(RolePermissionRepository rolePermissionRepository) {
    this.rolePermissionRepository = rolePermissionRepository;
  }

  @Transactional(readOnly = true)
  public List<String> resolvePermissions(UserEntity user) {
    return resolvePermissions(user.roleList());
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
