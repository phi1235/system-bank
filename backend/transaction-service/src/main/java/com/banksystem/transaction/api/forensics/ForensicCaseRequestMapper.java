package com.banksystem.transaction.api.forensics;

import com.banksystem.transaction.api.dto.ForensicCaseDtos.ForensicCaseFilterRequest;
import com.banksystem.transaction.application.forensics.ForensicCaseSearchQuery;

final class ForensicCaseRequestMapper {
  private ForensicCaseRequestMapper() {}

  static ForensicCaseSearchQuery toQuery(ForensicCaseFilterRequest request) {
    return ForensicCaseSearchQuery.of(
        request.q(), request.status(), request.priority(), request.assignedTo(),
        request.transactionId(), request.from(), request.to(), request.page(), request.size());
  }
}
