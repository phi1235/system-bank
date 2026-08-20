package com.banksystem.transaction.api.forensics;

import java.time.Instant;

import com.banksystem.transaction.api.dto.ForensicDtos.ForensicInvestigationFilterRequest;
import com.banksystem.transaction.api.dto.ForensicDtos.EvidenceTimelineFilterRequest;
import com.banksystem.transaction.application.forensics.EvidenceTimelineQuery;
import com.banksystem.transaction.application.forensics.ForensicSearchQuery;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
final class ForensicRequestMapper {

  ForensicRequestMapper() {
  }

  ForensicSearchQuery toQuery(ForensicInvestigationFilterRequest request) {
    String q = request.q();
    UUID txUuid = parseUuid(request.transactionId());
    if (txUuid == null && request.transactionId() != null && !request.transactionId().isBlank()) {
      if (q == null || q.isBlank()) {
        q = request.transactionId().trim();
      }
    }
    UUID accUuid = parseUuid(request.accountId());
    if (accUuid == null && request.accountId() != null && !request.accountId().isBlank()) {
      if (q == null || q.isBlank()) {
        q = request.accountId().trim();
      }
    }
    return ForensicSearchQuery.of(
        q,
        txUuid,
        accUuid,
        request.transferStatus(),
        request.riskDecision(),
        request.from(),
        request.to(),
        request.page(),
        request.size(),
        Instant.now());
  }

  private static UUID parseUuid(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      return UUID.fromString(raw.trim());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  EvidenceTimelineQuery toQuery(EvidenceTimelineFilterRequest request) {
    return EvidenceTimelineQuery.of(request.source(), request.page(), request.size());
  }
}
