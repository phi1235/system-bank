package com.banksystem.transaction.config;

import java.util.List;
import java.util.UUID;

public record GatewayUser(UUID userId, List<String> roles) {
  public boolean hasRole(String role) {
    return roles.stream().anyMatch(r -> r.equalsIgnoreCase(role) || r.equalsIgnoreCase("ROLE_" + role));
  }
}
