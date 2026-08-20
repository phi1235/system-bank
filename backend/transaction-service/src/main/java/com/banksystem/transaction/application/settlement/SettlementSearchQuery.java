package com.banksystem.transaction.application.settlement;

import com.banksystem.transaction.api.dto.SettlementDtos.AdminSettlementFilterRequest;
import com.banksystem.transaction.api.dto.SettlementDtos.SettlementFilterRequest;
import com.banksystem.transaction.domain.settlement.SettlementStatus;
import java.util.UUID;

public record SettlementSearchQuery(
    UUID organizationId,
    SettlementStatus status,
    int page,
    int size
) {
  public static final int DEFAULT_SIZE = 20;
  public static final int MAX_SIZE = 100;

  public static SettlementSearchQuery of(UUID businessId, SettlementFilterRequest req) {
    int p = req == null || req.page() == null || req.page() < 0 ? 0 : req.page();
    int s = req == null || req.size() == null ? DEFAULT_SIZE : req.size();
    if (s < 1) s = DEFAULT_SIZE;
    if (s > MAX_SIZE) s = MAX_SIZE;
    SettlementStatus status = req != null ? req.status() : null;
    return new SettlementSearchQuery(businessId, status, p, s);
  }

  public static SettlementSearchQuery of(AdminSettlementFilterRequest req) {
    int p = req == null || req.page() == null || req.page() < 0 ? 0 : req.page();
    int s = req == null || req.size() == null ? DEFAULT_SIZE : req.size();
    if (s < 1) s = DEFAULT_SIZE;
    if (s > MAX_SIZE) s = MAX_SIZE;
    SettlementStatus status = req != null ? req.status() : null;
    UUID orgId = req != null ? req.organizationId() : null;
    return new SettlementSearchQuery(orgId, status, p, s);
  }
}
