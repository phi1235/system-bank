package com.banksystem.transaction.application.collection;

import com.banksystem.transaction.api.dto.CollectionDtos.InboundPaymentFilterRequest;
import com.banksystem.transaction.domain.collection.InboundPaymentStatus;

public record InboundPaymentSearchQuery(
    String provider,
    String q,
    InboundPaymentStatus status,
    int page,
    int size
) {
  public static final int DEFAULT_SIZE = 20;
  public static final int MAX_SIZE = 100;

  public static InboundPaymentSearchQuery of(InboundPaymentFilterRequest req) {
    int p = req == null || req.page() == null || req.page() < 0 ? 0 : req.page();
    int s = req == null || req.size() == null ? DEFAULT_SIZE : req.size();
    if (s < 1) s = DEFAULT_SIZE;
    if (s > MAX_SIZE) s = MAX_SIZE;
    String pStr = req != null && req.provider() != null && !req.provider().isBlank() ? req.provider().trim() : null;
    String qStr = req != null && req.q() != null && !req.q().isBlank() ? req.q().trim() : null;
    InboundPaymentStatus status = req != null ? req.status() : null;
    return new InboundPaymentSearchQuery(pStr, qStr, status, p, s);
  }
}
