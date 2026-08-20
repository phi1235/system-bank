package com.banksystem.auth.api.business;

import com.banksystem.auth.api.dto.BusinessDtos.AddBusinessMemberRequest;
import com.banksystem.auth.api.dto.BusinessDtos.BusinessMemberResponse;
import com.banksystem.auth.api.dto.BusinessDtos.BusinessMembershipVerifyResponse;
import com.banksystem.auth.api.dto.BusinessDtos.BusinessOrganizationResponse;
import com.banksystem.auth.api.dto.BusinessDtos.CreateBusinessOrganizationRequest;
import com.banksystem.auth.api.dto.BusinessDtos.UpdateBusinessMemberRequest;
import com.banksystem.auth.application.business.BusinessOrganizationService;
import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.GatewayUser;
import com.banksystem.common.security.UserContext;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/businesses")
public class BusinessController {

  private final BusinessOrganizationService organizationService;

  public BusinessController(BusinessOrganizationService organizationService) {
    this.organizationService = organizationService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<BusinessOrganizationResponse> createOrganization(
      @Valid @RequestBody CreateBusinessOrganizationRequest request) {
    GatewayUser user = UserContext.requireUser();
    return ApiResponse.ok(organizationService.createOrganization(user.userId(), request));
  }

  @GetMapping
  public ApiResponse<List<BusinessOrganizationResponse>> listMyOrganizations() {
    GatewayUser user = UserContext.requireUser();
    return ApiResponse.ok(organizationService.listUserOrganizations(user.userId()));
  }

  @GetMapping("/{businessId}")
  public ApiResponse<BusinessOrganizationResponse> getOrganization(@PathVariable UUID businessId) {
    GatewayUser user = UserContext.requireUser();
    return ApiResponse.ok(organizationService.getOrganization(businessId, user.userId()));
  }

  @GetMapping("/{businessId}/members")
  public ApiResponse<List<BusinessMemberResponse>> listMembers(@PathVariable UUID businessId) {
    GatewayUser user = UserContext.requireUser();
    return ApiResponse.ok(organizationService.listMembers(businessId, user.userId()));
  }

  @PostMapping("/{businessId}/members")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<BusinessMemberResponse> addMember(
      @PathVariable UUID businessId,
      @Valid @RequestBody AddBusinessMemberRequest request) {
    GatewayUser user = UserContext.requireUser();
    return ApiResponse.ok(organizationService.addMember(businessId, user.userId(), request));
  }

  @PatchMapping("/{businessId}/members/{memberUserId}")
  public ApiResponse<BusinessMemberResponse> updateMemberRole(
      @PathVariable UUID businessId,
      @PathVariable UUID memberUserId,
      @Valid @RequestBody UpdateBusinessMemberRequest request) {
    GatewayUser user = UserContext.requireUser();
    return ApiResponse.ok(organizationService.updateMemberRole(businessId, memberUserId, user.userId(), request));
  }

  @DeleteMapping("/{businessId}/members/{memberUserId}")
  public ApiResponse<Void> removeMember(
      @PathVariable UUID businessId,
      @PathVariable UUID memberUserId) {
    GatewayUser user = UserContext.requireUser();
    organizationService.removeMember(businessId, memberUserId, user.userId());
    return ApiResponse.ok(null);
  }

  @GetMapping("/{businessId}/my-membership")
  public ApiResponse<BusinessMembershipVerifyResponse> getMyMembership(@PathVariable UUID businessId) {
    GatewayUser user = UserContext.requireUser();
    return ApiResponse.ok(organizationService.getMyMembership(businessId, user.userId()));
  }
}
