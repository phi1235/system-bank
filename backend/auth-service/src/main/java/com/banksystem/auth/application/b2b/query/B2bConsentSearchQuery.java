package com.banksystem.auth.application.b2b.query;

import com.banksystem.auth.api.dto.B2bDtos.B2bConsentFilterRequest;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public record B2bConsentSearchQuery(
    String clientId,
    UUID customerId,
    String status,
    String accountNumber,
    int page,
    int size) {

  public static B2bConsentSearchQuery of(B2bConsentFilterRequest req) {
    if (req == null) {
      return new B2bConsentSearchQuery(null, null, null, null, 0, 20);
    }
    String normalizedClientId = (req.clientId() != null && !req.clientId().isBlank()) ? req.clientId().trim() : null;
    String normalizedStatus = (req.status() != null && !req.status().isBlank()) ? req.status().trim().toUpperCase() : null;
    String normalizedAcc = (req.accountNumber() != null && !req.accountNumber().isBlank()) ? req.accountNumber().trim() : null;
    int normalizedPage = req.page() != null && req.page() >= 0 ? req.page() : 0;
    int normalizedSize = req.size() != null && req.size() >= 1 ? Math.min(req.size(), 100) : 20;
    return new B2bConsentSearchQuery(normalizedClientId, req.customerId(), normalizedStatus, normalizedAcc, normalizedPage, normalizedSize);
  }

  public Pageable toPageable() {
    return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
  }
}
