package com.banksystem.transaction.api.transfer;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.RequireInternalApiKey;
import com.banksystem.transaction.api.dto.TransferDtos.CorporatePayoutTransferRequest;
import com.banksystem.transaction.api.dto.TransferDtos.CorporatePayoutInquiryRequest;
import com.banksystem.transaction.api.dto.TransferDtos.InternalTransactionCountsResponse;
import com.banksystem.transaction.api.dto.TransferDtos.TransferResponse;
import com.banksystem.transaction.application.metrics.InternalTransactionQueryService;
import com.banksystem.transaction.application.transfer.TransferService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/transactions")
@RequireInternalApiKey
public class InternalTransactionController {

  private final InternalTransactionQueryService queryService;
  private final TransferService transferService;

  public InternalTransactionController(
      InternalTransactionQueryService queryService,
      TransferService transferService) {
    this.queryService = queryService;
    this.transferService = transferService;
  }

  @GetMapping("/counts")
  public ApiResponse<InternalTransactionCountsResponse> counts() {
    return ApiResponse.ok(queryService.counts());
  }

  @PostMapping("/corporate-payout")
  public ApiResponse<TransferResponse> executeCorporatePayout(
      @Valid @RequestBody CorporatePayoutTransferRequest request) {
    return ApiResponse.ok(transferService.executeCorporatePayout(request));
  }

  @GetMapping("/corporate-payout/inquiry")
  public ApiResponse<TransferResponse> inquireCorporatePayout(
      @Valid @ModelAttribute CorporatePayoutInquiryRequest request) {
    return ApiResponse.ok(transferService.inquireCorporatePayout(
        request.corporateId(), request.batchId(), request.idempotencyKey()));
  }
}
