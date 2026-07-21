package com.banksystem.notification.config;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public record GatewayUser(UUID userId, List<String> roles, List<String> permissions) {

  public GatewayUser {
    roles = roles == null ? List.of() : List.copyOf(roles);
    permissions = permissions == null ? List.of() : List.copyOf(permissions);
  }

  public boolean hasRole(String role) {
    return roles.stream().anyMatch(r -> equalsRole(r, role));
  }

  public boolean hasPermission(String permission) {
    if (permission == null) {
      return false;
    }
    if (hasRole("ADMIN") || hasRole("SUPER_ADMIN")) {
      return true;
    }
    return permissions.stream()
        .anyMatch(p -> p.equalsIgnoreCase(permission) || "*".equals(p));
  }

  private static boolean equalsRole(String actual, String expected) {
    if (actual == null || expected == null) {
      return false;
    }
    String a = actual.trim().toUpperCase(Locale.ROOT);
    String e = expected.trim().toUpperCase(Locale.ROOT);
    return a.equals(e) || a.equals("ROLE_" + e) || ("ROLE_" + a).equals(e);
  }
}
