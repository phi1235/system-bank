package com.banksystem.account.domain.enums.account;

import com.banksystem.common.exception.BusinessException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;

/** Supported account product types for open + admin filters. */
public enum AccountType {
  PAYMENT,
  SAVINGS;

  private static final Set<String> NAMES = Set.of(PAYMENT.name(), SAVINGS.name());

  public static Optional<AccountType> tryParse(String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    String normalized = raw.trim().toUpperCase(Locale.ROOT);
    if (!NAMES.contains(normalized)) {
      return Optional.empty();
    }
    return Optional.of(AccountType.valueOf(normalized));
  }

  public static AccountType parseRequired(String raw) {
    return tryParse(raw).orElseThrow(() -> new BusinessException(
        "INVALID_ACCOUNT_TYPE",
        "accountType must be PAYMENT|SAVINGS",
        HttpStatus.BAD_REQUEST));
  }
}
