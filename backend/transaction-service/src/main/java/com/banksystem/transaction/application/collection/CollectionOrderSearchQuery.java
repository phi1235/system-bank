package com.banksystem.transaction.application.collection;

import com.banksystem.transaction.api.dto.CollectionDtos.AdminCollectionOrderFilterRequest;
import com.banksystem.transaction.api.dto.CollectionDtos.CollectionOrderFilterRequest;
import com.banksystem.transaction.domain.collection.CollectionOrderStatus;
import java.util.UUID;

public record CollectionOrderSearchQuery(
    UUID organizationId,
    String q,
    CollectionOrderStatus status,
    int page,
    int size
) {
  public static final int DEFAULT_SIZE = 20;
  public static final int MAX_SIZE = 100;

  public static CollectionOrderSearchQuery of(UUID businessId, CollectionOrderFilterRequest req) {
    int p = req == null || req.page() == null || req.page() < 0 ? 0 : req.page();
    int s = req == null || req.size() == null ? DEFAULT_SIZE : req.size();
    if (s < 1) s = DEFAULT_SIZE;
    if (s > MAX_SIZE) s = MAX_SIZE;
    String qStr = req != null && req.q() != null && !req.q().isBlank() ? req.q().trim() : null;
    CollectionOrderStatus status = req != null ? req.status() : null;
    return new CollectionOrderSearchQuery(businessId, qStr, status, p, s);
  }

  public static CollectionOrderSearchQuery of(AdminCollectionOrderFilterRequest req) {
    int p = req == null || req.page() == null || req.page() < 0 ? 0 : req.page();
    int s = req == null || req.size() == null ? DEFAULT_SIZE : req.size();
    if (s < 1) s = DEFAULT_SIZE;
    if (s > MAX_SIZE) s = MAX_SIZE;
    String qStr = req != null && req.q() != null && !req.q().isBlank() ? req.q().trim() : null;
    CollectionOrderStatus status = req != null ? req.status() : null;
    UUID orgId = req != null ? req.organizationId() : null;
    return new CollectionOrderSearchQuery(orgId, qStr, status, p, s);
  }
}
