package com.banksystem.account.domain;

import com.banksystem.common.exception.BusinessException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Account lifecycle statuses used by both query filters and state transitions.
 */
public enum AccountStatus {
  ACTIVE,
  FROZEN,
  CLOSED;

  private static final Set<String> NAMES = Set.of(ACTIVE.name(), FROZEN.name(), CLOSED.name());

  public static Optional<AccountStatus> tryParse(String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    String normalized = raw.trim().toUpperCase(Locale.ROOT);
    if (!NAMES.contains(normalized)) {
      return Optional.empty();
    }
    return Optional.of(AccountStatus.valueOf(normalized));
  }

  public static AccountStatus parseRequired(String raw) {
    return tryParse(raw).orElseThrow(() -> new BusinessException(
        "INVALID_STATUS",
        "status must be ACTIVE|FROZEN|CLOSED"));
  }

  public boolean isActive() {
    return this == ACTIVE;
  }

  public boolean isFrozen() {
    return this == FROZEN;
  }

  public boolean isClosed() {
    return this == CLOSED;
  }
}
