package com.banksystem.account.application.account;
import com.banksystem.account.application.account.*;
import com.banksystem.account.application.card.*;
import com.banksystem.account.application.deposit.*;
import com.banksystem.account.application.ledger.*;
import com.banksystem.account.domain.account.*;
import com.banksystem.account.domain.card.*;
import com.banksystem.account.domain.deposit.*;
import com.banksystem.account.domain.ledger.*;
import com.banksystem.account.api.dto.*;

/**
 * Application-level search criteria for staff account listing.
 * Built by the controller (HTTP params) and interpreted by the app service.
 */
public record AdminAccountSearchQuery(
    String q,
    String status,
    String accountType,
    int page,
    int size
) {
  public static final int DEFAULT_PAGE = 0;
  public static final int DEFAULT_SIZE = 20;
  public static final int MAX_SIZE = 100;

  public AdminAccountSearchQuery {
    page = Math.max(page, 0);
    size = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    q = normalizeBlank(q);
    status = normalizeBlank(status);
    accountType = normalizeBlank(accountType);
  }

  public static AdminAccountSearchQuery of(
      String q, String status, String accountType, Integer page, Integer size) {
    int p = page == null || page < 0 ? DEFAULT_PAGE : page;
    int s = size == null || size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    return new AdminAccountSearchQuery(q, status, accountType, p, s);
  }

  /** Backward-compatible factory used by older call sites/tests. */
  public static AdminAccountSearchQuery of(String q, String status, Integer page, Integer size) {
    return of(q, status, null, page, size);
  }

  private static String normalizeBlank(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
