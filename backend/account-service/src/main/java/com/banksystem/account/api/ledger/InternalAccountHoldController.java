package com.banksystem.account.api.ledger;

import com.banksystem.account.api.dto.AccountHoldDtos.CreateHoldRequest;
import com.banksystem.account.api.dto.AccountHoldDtos.HoldActionRequest;
import com.banksystem.account.api.dto.AccountHoldDtos.HoldResponse;
import com.banksystem.account.application.ledger.AccountHoldService;
import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.RequireInternalApiKey;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/ledger/holds")
@RequireInternalApiKey
public class InternalAccountHoldController {
  private final AccountHoldService holdService;

  public InternalAccountHoldController(AccountHoldService holdService) {
    this.holdService = holdService;
  }

  @PostMapping("/accounts/{accountId}")
  public ApiResponse<HoldResponse> create(
      @PathVariable UUID accountId,
      @Valid @RequestBody CreateHoldRequest request) {
    return ApiResponse.ok(holdService.create(accountId, request));
  }

  @PostMapping("/{holdId}/capture")
  public ApiResponse<HoldResponse> capture(
      @PathVariable UUID holdId,
      @Valid @RequestBody HoldActionRequest request) {
    return ApiResponse.ok(holdService.capture(holdId, request));
  }

  @PostMapping("/{holdId}/release")
  public ApiResponse<HoldResponse> release(
      @PathVariable UUID holdId,
      @Valid @RequestBody HoldActionRequest request) {
    return ApiResponse.ok(holdService.release(holdId, request));
  }
}
