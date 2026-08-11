package com.banksystem.transaction.api.transfer;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.RequireInternalApiKey;
import com.banksystem.transaction.api.dto.TransferDtos.InternalTransactionCountsResponse;
import com.banksystem.transaction.application.metrics.InternalTransactionQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/transactions")
@RequireInternalApiKey
public class InternalTransactionController {

  private final InternalTransactionQueryService queryService;

  public InternalTransactionController(InternalTransactionQueryService queryService) {
    this.queryService = queryService;
  }

  @GetMapping("/counts")
  public ApiResponse<InternalTransactionCountsResponse> counts() {
    return ApiResponse.ok(queryService.counts());
  }
}
