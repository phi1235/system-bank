package com.banksystem.transaction.application.virtualaccount;

import com.banksystem.transaction.api.dto.VirtualAccountDtos.AdminVirtualAccountFilterRequest;
import com.banksystem.transaction.api.dto.VirtualAccountDtos.VirtualAccountFilterRequest;
import com.banksystem.transaction.domain.virtualaccount.VirtualAccountStatus;
import java.util.UUID;

public record VirtualAccountSearchQuery(
    UUID organizationId,
    String q,
    VirtualAccountStatus status,
    int page,
    int size
) {
  public static final int DEFAULT_SIZE = 20;
  public static final int MAX_SIZE = 100;

  public static VirtualAccountSearchQuery of(UUID businessId, VirtualAccountFilterRequest req) {
    int p = req == null || req.page() == null || req.page() < 0 ? 0 : req.page();
    int s = req == null || req.size() == null ? DEFAULT_SIZE : req.size();
    if (s < 1) s = DEFAULT_SIZE;
    if (s > MAX_SIZE) s = MAX_SIZE;
    String qStr = req != null && req.q() != null && !req.q().isBlank() ? req.q().trim() : null;
    VirtualAccountStatus status = req != null ? req.status() : null;
    return new VirtualAccountSearchQuery(businessId, qStr, status, p, s);
  }

  public static VirtualAccountSearchQuery of(AdminVirtualAccountFilterRequest req) {
    int p = req == null || req.page() == null || req.page() < 0 ? 0 : req.page();
    int s = req == null || req.size() == null ? DEFAULT_SIZE : req.size();
    if (s < 1) s = DEFAULT_SIZE;
    if (s > MAX_SIZE) s = MAX_SIZE;
    String qStr = req != null && req.q() != null && !req.q().isBlank() ? req.q().trim() : null;
    VirtualAccountStatus status = req != null ? req.status() : null;
    UUID orgId = req != null ? req.organizationId() : null;
    return new VirtualAccountSearchQuery(orgId, qStr, status, p, s);
  }
}
