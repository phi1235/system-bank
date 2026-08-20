package com.banksystem.corporate.infrastructure.feign;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.SecurityHeaders;
import com.banksystem.corporate.infrastructure.feign.FeignClientDtos.AccountDto;
import com.banksystem.corporate.infrastructure.feign.FeignClientDtos.AccountOwnershipDto;
import com.banksystem.corporate.infrastructure.feign.FeignClientDtos.CreateBatchHoldReq;
import com.banksystem.corporate.infrastructure.feign.FeignClientDtos.CreateCorporateAccountReq;
import com.banksystem.corporate.infrastructure.feign.FeignClientDtos.HoldActionReq;
import com.banksystem.corporate.infrastructure.feign.FeignClientDtos.HoldResult;
import com.banksystem.corporate.infrastructure.feign.FeignClientDtos.PartialCaptureHoldReq;
import java.util.List;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "ACCOUNT-SERVICE", url = "${bank.feign.account-url:}")
public interface AccountClient {

  @GetMapping("/internal/accounts/{id}")
  ApiResponse<AccountDto> getAccount(
      @RequestHeader(SecurityHeaders.INTERNAL_API_KEY) String apiKey,
      @PathVariable("id") UUID id);

  @GetMapping("/internal/accounts/{id}/ownership")
  ApiResponse<AccountOwnershipDto> getOwnership(
      @RequestHeader(SecurityHeaders.INTERNAL_API_KEY) String apiKey,
      @PathVariable("id") UUID id);

  @GetMapping("/internal/accounts/by-number/{accountNumber}")
  ApiResponse<AccountDto> getAccountByNumber(
      @RequestHeader(SecurityHeaders.INTERNAL_API_KEY) String apiKey,
      @PathVariable("accountNumber") String accountNumber);

  @GetMapping("/internal/accounts/owners/{ownerType}/{ownerId}")
  ApiResponse<List<AccountDto>> listByOwner(
      @RequestHeader(SecurityHeaders.INTERNAL_API_KEY) String apiKey,
      @PathVariable("ownerType") String ownerType,
      @PathVariable("ownerId") UUID ownerId);

  @PostMapping("/internal/accounts/corporate")
  ApiResponse<AccountDto> createCorporateAccount(
      @RequestHeader(SecurityHeaders.INTERNAL_API_KEY) String apiKey,
      @RequestBody CreateCorporateAccountReq req);

  @PostMapping("/internal/ledger/holds/batch/accounts/{accountId}")
  ApiResponse<HoldResult> createBatchHold(
      @RequestHeader(SecurityHeaders.INTERNAL_API_KEY) String apiKey,
      @PathVariable("accountId") UUID accountId,
      @RequestBody CreateBatchHoldReq req);

  @PostMapping("/internal/ledger/holds/{holdId}/partial-capture")
  ApiResponse<HoldResult> partialCapture(
      @RequestHeader(SecurityHeaders.INTERNAL_API_KEY) String apiKey,
      @PathVariable("holdId") UUID holdId,
      @RequestBody PartialCaptureHoldReq req);

  @PostMapping("/internal/ledger/holds/{holdId}/release-remaining")
  ApiResponse<HoldResult> releaseRemaining(
      @RequestHeader(SecurityHeaders.INTERNAL_API_KEY) String apiKey,
      @PathVariable("holdId") UUID holdId,
      @RequestBody HoldActionReq req);

  @PostMapping("/internal/ledger/holds/{holdId}/release")
  ApiResponse<HoldResult> release(
      @RequestHeader(SecurityHeaders.INTERNAL_API_KEY) String apiKey,
      @PathVariable("holdId") UUID holdId,
      @RequestBody HoldActionReq req);
}
