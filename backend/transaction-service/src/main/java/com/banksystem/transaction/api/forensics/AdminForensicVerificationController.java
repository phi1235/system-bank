package com.banksystem.transaction.api.forensics;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.common.security.SecurityHeaders;
import com.banksystem.common.security.UserContext;
import com.banksystem.transaction.api.dto.ForensicVerificationDtos.VerificationRunResponse;
import com.banksystem.transaction.application.forensics.ForensicVerificationService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/forensics/verification")
public class AdminForensicVerificationController {
  private final ForensicVerificationService service;

  public AdminForensicVerificationController(ForensicVerificationService service) {
    this.service = service;
  }

  @PostMapping("/check/{transactionId}")
  @RequirePermission(SecurityHeaders.PERM_FORENSICS_VERIFY_EXECUTE)
  public ApiResponse<VerificationRunResponse> check(
      @PathVariable UUID transactionId,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    return ApiResponse.ok(
        service.check(transactionId, UserContext.requireUser().userId(), idempotencyKey));
  }

  @GetMapping("/runs/{runId}")
  @RequirePermission(SecurityHeaders.PERM_FORENSICS_VIEW)
  public ApiResponse<VerificationRunResponse> get(@PathVariable UUID runId) {
    return ApiResponse.ok(service.get(runId));
  }
}
