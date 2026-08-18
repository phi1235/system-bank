package com.banksystem.transaction.api.forensics;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.common.security.SecurityHeaders;
import com.banksystem.transaction.api.dto.ForensicDtos.TemporalAccountStateResponse;
import com.banksystem.transaction.api.dto.ForensicDtos.TemporalInvestigationStateResponse;
import com.banksystem.transaction.application.forensics.ForensicInvestigationQueryService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/forensics/temporal")
@RequirePermission(SecurityHeaders.PERM_FORENSICS_VIEW)
public class AdminForensicTemporalController {
  private final ForensicInvestigationQueryService service;

  public AdminForensicTemporalController(ForensicInvestigationQueryService service) {
    this.service = service;
  }

  @GetMapping("/accounts/{accountId}")
  public ApiResponse<TemporalAccountStateResponse> account(
      @PathVariable UUID accountId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant at) {
    return ApiResponse.ok(service.temporalAccountState(accountId, at));
  }

  @GetMapping("/transactions/{transactionId}")
  public ApiResponse<TemporalInvestigationStateResponse> transaction(
      @PathVariable UUID transactionId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant at) {
    return ApiResponse.ok(service.temporalState(transactionId, at));
  }
}
