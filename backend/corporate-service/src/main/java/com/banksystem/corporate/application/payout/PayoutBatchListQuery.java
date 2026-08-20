package com.banksystem.corporate.application.payout;

import com.banksystem.corporate.api.dto.PayoutBatchDtos.PayoutPageRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

public record PayoutBatchListQuery(int page, int size) {

  private static final int DEFAULT_SIZE = 20;
  private static final int MAX_SIZE = 100;

  public static PayoutBatchListQuery of(PayoutPageRequest request) {
    int normalizedPage = request.page() == null ? 0 : Math.max(0, request.page());
    int requestedSize = request.size() == null ? DEFAULT_SIZE : request.size();
    return new PayoutBatchListQuery(normalizedPage, Math.min(MAX_SIZE, Math.max(1, requestedSize)));
  }

  public Pageable pageable() {
    return PageRequest.of(page, size);
  }
}
