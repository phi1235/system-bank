package com.banksystem.auth.application.permission;

import com.banksystem.auth.config.UserPrincipal;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.common.security.SecurityHeaders;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class PermissionChecker {

  private static final Set<String> FULL_ADMIN_ROLES = Set.of("ADMIN", "SUPER_ADMIN");

  private PermissionChecker() {}

  public static boolean isFullAdmin(UserPrincipal p) {
    if (p == null || p.roles() == null) {
      return false;
    }
    return p.roles().stream().anyMatch(r -> {
      if (r == null) {
        return false;
      }
      String n = r.trim().toUpperCase(Locale.ROOT);
      if (n.startsWith("ROLE_")) {
        n = n.substring(5);
      }
      return FULL_ADMIN_ROLES.contains(n);
    });
  }

  public static void requirePermission(List<String> permissions, String required) {
    if (permissions != null && permissions.stream().anyMatch(p -> p.equalsIgnoreCase(required) || "*".equals(p))) {
      return;
    }
    throw new BusinessException("FORBIDDEN", "Missing permission: " + required);
  }

  public static void requireRbacAccess(UserPrincipal p) {
    if (isFullAdmin(p)
        || PermissionUtils.hasAny(
            p.permissions(),
            SecurityHeaders.PERM_RBAC_ACCESS,
            SecurityHeaders.PERM_RBAC_USERS_ASSIGN,
            SecurityHeaders.PERM_RBAC_ROLES_MANAGE,
            "rbac:manage")) {
      return;
    }
    throw new BusinessException("FORBIDDEN", "Missing permission: rbac:access");
  }

  public static void requireUsersAssign(UserPrincipal p) {
    if (isFullAdmin(p)
        || PermissionUtils.hasAny(
            p.permissions(),
            SecurityHeaders.PERM_RBAC_USERS_ASSIGN,
            "rbac:manage")) {
      return;
    }
    throw new BusinessException("FORBIDDEN", "Missing permission: rbac:users:assign");
  }

  public static void requireRolesManage(UserPrincipal p) {
    if (isFullAdmin(p)
        || PermissionUtils.hasAny(
            p.permissions(),
            SecurityHeaders.PERM_RBAC_ROLES_MANAGE,
            "rbac:manage")) {
      return;
    }
    throw new BusinessException("FORBIDDEN", "Missing permission: rbac:roles:manage");
  }

  public static void requirePasswordReset(UserPrincipal p) {
    if (isFullAdmin(p) || PermissionUtils.hasAny(p.permissions(), "users:password:reset", "rbac:manage")) {
      return;
    }
    throw new BusinessException("FORBIDDEN", "Missing permission: users:password:reset");
  }

  public static void requireUserLock(UserPrincipal p) {
    if (isFullAdmin(p) || PermissionUtils.hasAny(p.permissions(), "users:lock:execute", "rbac:manage")) {
      return;
    }
    throw new BusinessException("FORBIDDEN", "Missing permission: users:lock:execute");
  }
}
