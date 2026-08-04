package com.banksystem.auth.application.permission;

import java.util.Arrays;
import java.util.List;

public final class PermissionUtils {

  private PermissionUtils() {}

  public static List<String> parseCsv(String csv) {
    if (csv == null || csv.isBlank()) {
      return List.of();
    }
    return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
  }

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
