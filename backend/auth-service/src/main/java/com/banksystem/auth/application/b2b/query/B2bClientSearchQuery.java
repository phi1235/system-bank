package com.banksystem.auth.application.b2b.query;

import com.banksystem.auth.api.dto.B2bDtos.B2bClientFilterRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public record B2bClientSearchQuery(
    String q,
    String status,
    int page,
    int size) {

  public static B2bClientSearchQuery of(B2bClientFilterRequest req) {
    if (req == null) {
      return new B2bClientSearchQuery(null, null, 0, 20);
    }
    String normalizedQ = (req.q() != null && !req.q().isBlank()) ? req.q().trim() : null;
    String normalizedStatus = (req.status() != null && !req.status().isBlank()) ? req.status().trim().toUpperCase() : null;
    int normalizedPage = req.page() != null && req.page() >= 0 ? req.page() : 0;
    int normalizedSize = req.size() != null && req.size() >= 1 ? Math.min(req.size(), 100) : 20;
    return new B2bClientSearchQuery(normalizedQ, normalizedStatus, normalizedPage, normalizedSize);
  }

  public Pageable toPageable() {
    return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
  }
}
