package com.banksystem.account.domain.ledger;
import com.banksystem.account.application.account.*;
import com.banksystem.account.application.card.*;
import com.banksystem.account.application.deposit.*;
import com.banksystem.account.application.ledger.*;
import com.banksystem.account.domain.account.*;
import com.banksystem.account.domain.card.*;
import com.banksystem.account.domain.deposit.*;
import com.banksystem.account.domain.ledger.*;
import com.banksystem.account.api.dto.*;

import com.banksystem.common.exception.BusinessException;

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
          "entryType must be DEBIT or CREDIT");
    }
  }
}
