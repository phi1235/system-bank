package com.banksystem.account.application.query;

import com.banksystem.account.domain.LedgerEntryType;
import java.time.Instant;
import java.util.UUID;

/**
 * Application query for account ledger statement.
 * Page size policy lives here (not in the controller).
 */
public final class LedgerStatementQuery {

  public static final int DEFAULT_SIZE = 20;
  public static final int MAX_SIZE = 100;

  private final UUID accountId;
  private final int page;
  private final int size;
  private final LedgerEntryType entryType;
  private final Instant from;
  private final Instant to;

  private LedgerStatementQuery(
      UUID accountId,
      int page,
      int size,
      LedgerEntryType entryType,
      Instant from,
      Instant to) {
    this.accountId = accountId;
    this.page = page;
    this.size = size;
    this.entryType = entryType;
    this.from = from;
    this.to = to;
  }

  public static LedgerStatementQuery of(
      UUID accountId,
      Integer page,
      Integer size,
      String entryType,
      Instant from,
      Instant to) {
    int p = page == null || page < 0 ? 0 : page;
    int s = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    LedgerEntryType type = LedgerEntryType.parseOptional(entryType);
    if (from != null && to != null && from.isAfter(to)) {
      throw new com.banksystem.common.exception.BusinessException(
          "INVALID_DATE_RANGE",
          "from must be before or equal to to",
          org.springframework.http.HttpStatus.BAD_REQUEST);
    }
    return new LedgerStatementQuery(accountId, p, s, type, from, to);
  }

  public UUID accountId() {
    return accountId;
  }

  public int page() {
    return page;
  }

  public int size() {
    return size;
  }

  public LedgerEntryType entryType() {
    return entryType;
  }

  public Instant from() {
    return from;
  }

  public Instant to() {
    return to;
  }
}
