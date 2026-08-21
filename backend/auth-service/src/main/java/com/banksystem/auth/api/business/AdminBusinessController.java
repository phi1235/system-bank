package com.banksystem.auth.api.business;

import com.banksystem.auth.api.dto.BusinessDtos.AdminBusinessResponse;
import com.banksystem.auth.api.dto.BusinessDtos.AdminKycReviewRequest;
import com.banksystem.auth.api.dto.BusinessDtos.BusinessOrganizationResponse;
import com.banksystem.auth.application.business.BusinessOrganizationService;
import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.GatewayUser;
import com.banksystem.common.security.UserContext;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/businesses")
public class AdminBusinessController {

  private final BusinessOrganizationService organizationService;

  public AdminBusinessController(BusinessOrganizationService organizationService) {
    this.organizationService = organizationService;
  }

  @GetMapping
  public ApiResponse<List<AdminBusinessResponse>> listBusinesses(
      @RequestParam(required = false) String kycStatus) {
    UserContext.requireAnyPermission("ADMIN", "OPS_ADMIN", "SUPER_ADMIN", "KYC_OFFICER", "customers:kyc:view", "customers:kyc:review", "customers:kyc:approve");
    return ApiResponse.ok(organizationService.listAdminBusinesses(kycStatus));
  }

  @PutMapping("/{businessId}/review")
  public ApiResponse<BusinessOrganizationResponse> reviewKyc(
      @PathVariable UUID businessId,
      @Valid @RequestBody AdminKycReviewRequest request) {
    GatewayUser adminUser = UserContext.requireUser();
    UserContext.requireAnyPermission("ADMIN", "OPS_ADMIN", "SUPER_ADMIN", "KYC_OFFICER", "customers:kyc:manage", "customers:kyc:approve", "customers:kyc:decide");
    return ApiResponse.ok(organizationService.reviewKyc(businessId, adminUser.userId(), request));
  }

}
