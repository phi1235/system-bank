package com.banksystem.transaction.api.forensics;

import java.time.Instant;

import com.banksystem.transaction.api.dto.ForensicCaseDtos.ForensicCaseFilterRequest;
import com.banksystem.transaction.application.forensics.ForensicCaseSearchQuery;
import org.springframework.stereotype.Component;

@Component
final class ForensicCaseRequestMapper {

  ForensicCaseRequestMapper() {
  }

  ForensicCaseSearchQuery toQuery(ForensicCaseFilterRequest request) {
    return ForensicCaseSearchQuery.of(
        request.q(), request.status(), request.priority(), request.assignedTo(),
        request.transactionId(), request.from(), request.to(), request.page(), request.size(),
        Instant.now());
  }
}
