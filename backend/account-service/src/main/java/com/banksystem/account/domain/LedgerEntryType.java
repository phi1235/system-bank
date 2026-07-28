package com.banksystem.account.domain;

import com.banksystem.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Money movement direction on an account ledger.
 * Stored as string in DB for Flyway/JPA simplicity.
 */
public enum LedgerEntryType {
  DEBIT,
  CREDIT;

  public static LedgerEntryType parseOptional(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return LedgerEntryType.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      throw new BusinessException(
          "INVALID_ENTRY_TYPE",
          "entryType must be DEBIT or CREDIT",
          HttpStatus.BAD_REQUEST);
    }
  }
}
