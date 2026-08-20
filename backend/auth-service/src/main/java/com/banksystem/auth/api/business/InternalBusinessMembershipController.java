package com.banksystem.auth.api.business;

import com.banksystem.auth.api.dto.BusinessDtos.BusinessMembershipVerifyResponse;
import com.banksystem.auth.application.business.BusinessOrganizationService;
import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.RequireInternalApiKey;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/businesses")
@RequireInternalApiKey
public class InternalBusinessMembershipController {

  private final BusinessOrganizationService organizationService;

  public InternalBusinessMembershipController(BusinessOrganizationService organizationService) {
    this.organizationService = organizationService;
  }

  @GetMapping("/{businessId}/members/{userId}/verify")
  public ApiResponse<BusinessMembershipVerifyResponse> verify(
      @PathVariable UUID businessId,
      @PathVariable UUID userId,
      @RequestParam(required = false) String permission) {
    return ApiResponse.ok(organizationService.verifyMembership(businessId, userId, permission));
  }
}
