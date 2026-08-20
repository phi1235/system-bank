package com.banksystem.transaction.application.openbanking;

import com.banksystem.transaction.api.dto.OpenBankingDtos.OpenBankingRecordFilterRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

public record OpenBankingRecordSearchQuery(
    String clientId,
    String messageId,
    String status,
    int page,
    int size) {

  public static OpenBankingRecordSearchQuery of(String clientId, OpenBankingRecordFilterRequest req) {
    if (req == null) {
      return new OpenBankingRecordSearchQuery(clientId, null, null, 0, 20);
    }
    String normalizedStatus = (req.status() != null && !req.status().isBlank()) ? req.status().trim().toUpperCase() : null;
    String normalizedMsgId = (req.messageId() != null && !req.messageId().isBlank()) ? req.messageId().trim() : null;
    int p = (req.page() != null && req.page() >= 0) ? req.page() : 0;
    int s = (req.size() != null && req.size() >= 1) ? Math.min(req.size(), 100) : 20;
    return new OpenBankingRecordSearchQuery(clientId, normalizedMsgId, normalizedStatus, p, s);
  }

  public Pageable toPageable() {
    return PageRequest.of(page, size);
  }
}
