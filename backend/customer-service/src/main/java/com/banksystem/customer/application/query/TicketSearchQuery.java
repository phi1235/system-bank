package com.banksystem.customer.application.query;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.customer.api.dto.SupportTicketDtos.AdminTicketFilterRequest;
import java.util.Locale;
import java.util.Set;

public record TicketSearchQuery(
    String statusNorm,
    String categoryNorm,
    String qNorm,
    int page,
    int size
) {

  private static final Set<String> CATEGORIES =
      Set.of("GENERAL", "ACCOUNT", "TRANSFER", "CARD", "KYC", "SECURITY", "OTHER");

  public static TicketSearchQuery of(AdminTicketFilterRequest req) {
    if (req == null) {
      return new TicketSearchQuery(null, null, null, 0, 20);
    }
    return of(req.status(), req.category(), req.q(), req.page(), req.size());
  }

  public static TicketSearchQuery of(String status, String category, String q, Integer page, Integer size) {
    String st = blankToNull(status);
    if (st != null) {
      st = st.trim().toUpperCase(Locale.ROOT);
    }

    String cat = blankToNull(category);
    if (cat != null) {
      cat = cat.trim().toUpperCase(Locale.ROOT);
      if (!CATEGORIES.contains(cat)) {
        throw new BusinessException("INVALID_CATEGORY", "Category must be one of " + CATEGORIES);
      }
    }

    String query = blankToNull(q);
    int p = (page == null || page < 0) ? 0 : page;
    int s = (size == null || size < 1) ? 20 : Math.min(size, 100);

    return new TicketSearchQuery(st, cat, query, p, s);
  }

  private static String blankToNull(String v) {
    if (v == null || v.isBlank()) {
      return null;
    }
    return v.trim();
  }
}
