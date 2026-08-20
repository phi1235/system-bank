package com.banksystem.corporate.infrastructure.feign;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.SecurityHeaders;
import com.banksystem.corporate.infrastructure.feign.FeignClientDtos.CorporatePayoutTransferReq;
import com.banksystem.corporate.infrastructure.feign.FeignClientDtos.TransferResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.UUID;

@FeignClient(name = "TRANSACTION-SERVICE", url = "${bank.feign.transaction-url:}")
public interface TransactionClient {

  @PostMapping("/internal/transactions/corporate-payout")
  ApiResponse<TransferResult> executeCorporatePayout(
      @RequestHeader(SecurityHeaders.INTERNAL_API_KEY) String apiKey,
      @RequestBody CorporatePayoutTransferReq req);

  @GetMapping("/internal/transactions/corporate-payout/inquiry")
  ApiResponse<TransferResult> inquireCorporatePayout(
      @RequestHeader(SecurityHeaders.INTERNAL_API_KEY) String apiKey,
      @RequestParam UUID corporateId,
      @RequestParam UUID batchId,
      @RequestParam String idempotencyKey);
}
