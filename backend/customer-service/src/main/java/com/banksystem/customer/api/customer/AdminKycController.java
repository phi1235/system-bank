package com.banksystem.customer.api.customer;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.RequireAnyPermission;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.common.security.UserContext;
import com.banksystem.customer.api.dto.KycDtos.CheckerDecisionRequest;
import com.banksystem.customer.api.dto.KycDtos.KycCaseResponse;
import com.banksystem.customer.application.kyc.KycWorkflowService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/kyc")
public class AdminKycController {

  private final KycWorkflowService service;

  public AdminKycController(KycWorkflowService service) {
    this.service = service;
  }

  @GetMapping("/customers/{customerId}")
  @RequireAnyPermission({"customers:kyc:review", "customers:kyc:approve"})
  public ApiResponse<KycCaseResponse> getByCustomer(@PathVariable UUID customerId) {
    return ApiResponse.ok(service.getByCustomer(customerId));
  }

  @PostMapping("/cases/{caseId}/decision")
  @RequirePermission("customers:kyc:approve")
  public ApiResponse<KycCaseResponse> checkerDecision(
      @PathVariable UUID caseId, @Valid @RequestBody CheckerDecisionRequest request) {
    return ApiResponse.ok(service.checkerDecision(
        caseId, UserContext.requireUser().userId(), request));
  }

  @GetMapping("/documents/{id}/content")
  @RequireAnyPermission({"customers:kyc:review", "customers:kyc:approve"})
  public ResponseEntity<InputStreamResource> download(@PathVariable UUID id) {
    return CustomerKycController.fileResponse(
        service.download(id, UserContext.requireUser().userId(), true));
  }
}
