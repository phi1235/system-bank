package com.banksystem.account.application.query;

/**
 * Application-level search criteria for staff account listing.
 * Built by the controller (HTTP params) and interpreted by the app service.
 */
public record AdminAccountSearchQuery(
    String q,
    String status,
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
  }

  public static AdminAccountSearchQuery of(String q, String status, int page, int size) {
    return new AdminAccountSearchQuery(q, status, page, size);
  }

  private static String normalizeBlank(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
