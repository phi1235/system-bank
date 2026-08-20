package com.banksystem.common.security;

import java.util.Arrays;
import java.util.List;

public record B2bClientPrincipal(
    String clientId,
    List<String> scopes,
    String certThumbprint,
    String organizationTaxCode) {

  public B2bClientPrincipal {
    scopes = scopes == null ? List.of() : List.copyOf(scopes);
  }

  public boolean hasScope(String scope) {
    if (scope == null || scope.isBlank()) {
      return false;
    }
    return scopes.stream().anyMatch(s -> s.equalsIgnoreCase(scope) || "*".equals(s));
  }

  public boolean hasAnyScope(String... requiredScopes) {
    if (requiredScopes == null || requiredScopes.length == 0) {
      return false;
    }
    return Arrays.stream(requiredScopes).anyMatch(this::hasScope);
  }
}
