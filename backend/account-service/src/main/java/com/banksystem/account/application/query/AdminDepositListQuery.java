package com.banksystem.account.application.query;

import com.banksystem.account.domain.TermDepositStatus;
import com.banksystem.common.exception.BusinessException;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Admin term-deposit search. Optional filters use boolean flags + non-null sentinels so
 * Postgres never sees an untyped NULL bind (42P18 — same pattern as the statement search).
 */
public final class AdminDepositListQuery {

  public static final int DEFAULT_SIZE = 20;
  public static final int MAX_SIZE = 100;
  public static final LocalDate MIN_DATE = LocalDate.of(1970, 1, 1);
  public static final LocalDate MAX_DATE = LocalDate.of(9999, 12, 31);
  private static final UUID NIL_UUID = new UUID(0L, 0L);

  private final int page;
  private final int size;
  private final TermDepositStatus status;
  private final String productCode;
  private final UUID userId;
  private final UUID accountId;
  private final String accountNumber;
  private final LocalDate maturityFrom;
  private final LocalDate maturityTo;

  private AdminDepositListQuery(
      int page,
      int size,
      TermDepositStatus status,
      String productCode,
      UUID userId,
      UUID accountId,
      String accountNumber,
      LocalDate maturityFrom,
      LocalDate maturityTo) {
    this.page = page;
    this.size = size;
    this.status = status;
    this.productCode = productCode;
    this.userId = userId;
    this.accountId = accountId;
    this.accountNumber = accountNumber;
    this.maturityFrom = maturityFrom;
    this.maturityTo = maturityTo;
  }

  public static AdminDepositListQuery of(
      Integer page,
      Integer size,
      String status,
      String productCode,
      String userId,
      String accountId,
      String accountNumber,
      LocalDate maturityFrom,
      LocalDate maturityTo) {
    int p = page == null || page < 0 ? 0 : page;
    int s = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    TermDepositStatus parsedStatus = parseStatus(status);
    if (maturityFrom != null && maturityTo != null && maturityFrom.isAfter(maturityTo)) {
      throw new BusinessException(
          "INVALID_DATE_RANGE", "maturityFrom must be before or equal to maturityTo",
          HttpStatus.BAD_REQUEST);
    }
    return new AdminDepositListQuery(
        p,
        s,
        parsedStatus,
        productCode == null || productCode.isBlank() ? null : productCode.trim(),
        parseUuid(userId, "userId"),
        parseUuid(accountId, "accountId"),
        accountNumber == null || accountNumber.isBlank() ? null : accountNumber.trim(),
        maturityFrom == null ? MIN_DATE : maturityFrom,
        maturityTo == null ? MAX_DATE : maturityTo);
  }

  private static TermDepositStatus parseStatus(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return TermDepositStatus.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      throw new BusinessException(
          "DEPOSIT_STATUS_INVALID", "Unknown deposit status: " + raw, HttpStatus.BAD_REQUEST);
    }
  }

  private static UUID parseUuid(String raw, String field) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(raw.trim());
    } catch (IllegalArgumentException ex) {
      throw new BusinessException(
          "INVALID_UUID", field + " must be a UUID", HttpStatus.BAD_REQUEST);
    }
  }

  public int page() {
    return page;
  }

  public int size() {
    return size;
  }

  public boolean hasStatus() {
    return status != null;
  }

  /** Never null — sentinel when unfiltered (guarded by the flag in the query). */
  public TermDepositStatus statusOrDefault() {
    return status == null ? TermDepositStatus.OPEN : status;
  }

  public boolean hasProduct() {
    return productCode != null;
  }

  public String productCodeOrEmpty() {
    return productCode == null ? "" : productCode;
  }

  public boolean hasUser() {
    return userId != null;
  }

  public UUID userIdOrNil() {
    return userId == null ? NIL_UUID : userId;
  }

  public boolean hasAccount() {
    return accountId != null;
  }

  public UUID accountIdOrNil() {
    return accountId == null ? NIL_UUID : accountId;
  }

  /** Human search key (STK); resolved to an account id by the service, not bound in SQL. */
  public String accountNumber() {
    return accountNumber;
  }

  public boolean hasAccountNumber() {
    return accountNumber != null;
  }

  public LocalDate maturityFrom() {
    return maturityFrom;
  }

  public LocalDate maturityTo() {
    return maturityTo;
  }
}
