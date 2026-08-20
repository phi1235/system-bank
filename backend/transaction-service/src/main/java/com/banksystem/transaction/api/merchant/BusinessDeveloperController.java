package com.banksystem.transaction.api.merchant;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.transaction.api.dto.MerchantDtos.ApiCredentialCreatedResponse;
import com.banksystem.transaction.api.dto.MerchantDtos.ApiCredentialResponse;
import com.banksystem.transaction.api.dto.MerchantDtos.ConfigureMerchantAccountRequest;
import com.banksystem.transaction.api.dto.MerchantDtos.CreateApiCredentialRequest;
import com.banksystem.transaction.api.dto.MerchantDtos.MerchantAccountResponse;
import com.banksystem.transaction.api.dto.MerchantDtos.RegisterWebhookEndpointRequest;
import com.banksystem.transaction.api.dto.MerchantDtos.WebhookEndpointCreatedResponse;
import com.banksystem.transaction.api.dto.MerchantDtos.WebhookEndpointResponse;
import com.banksystem.transaction.application.merchant.BusinessDeveloperService;
import com.banksystem.transaction.infrastructure.security.RequireBusinessPermission;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/businesses/{businessId}")
public class BusinessDeveloperController {

  private static final String MANAGE_CREDENTIALS = "business:credentials:manage";
  private final BusinessDeveloperService service;

  public BusinessDeveloperController(BusinessDeveloperService service) {
    this.service = service;
  }

  @GetMapping("/merchant-account")
  @RequireBusinessPermission(value = MANAGE_CREDENTIALS, businessIdParam = "businessId")
  public ApiResponse<MerchantAccountResponse> getMerchantAccount(@PathVariable UUID businessId) {
    return ApiResponse.ok(service.getMerchantAccount(businessId));
  }

  @PostMapping("/merchant-account")
  @RequireBusinessPermission(value = MANAGE_CREDENTIALS, businessIdParam = "businessId")
  public ApiResponse<MerchantAccountResponse> configureMerchantAccount(
      @PathVariable UUID businessId,
      @Valid @RequestBody ConfigureMerchantAccountRequest request) {
    return ApiResponse.ok(service.configureMerchantAccount(businessId, request));
  }

  @GetMapping("/credentials")
  @RequireBusinessPermission(value = MANAGE_CREDENTIALS, businessIdParam = "businessId")
  public ApiResponse<List<ApiCredentialResponse>> listCredentials(@PathVariable UUID businessId) {
    return ApiResponse.ok(service.listCredentials(businessId));
  }

  @PostMapping("/credentials")
  @ResponseStatus(HttpStatus.CREATED)
  @RequireBusinessPermission(value = MANAGE_CREDENTIALS, businessIdParam = "businessId")
  public ApiResponse<ApiCredentialCreatedResponse> createCredential(
      @PathVariable UUID businessId,
      @Valid @RequestBody CreateApiCredentialRequest request) {
    return ApiResponse.ok(service.createCredential(businessId, request));
  }

  @DeleteMapping("/credentials/{id}")
  @RequireBusinessPermission(value = MANAGE_CREDENTIALS, businessIdParam = "businessId")
  public ApiResponse<Void> revokeCredential(
      @PathVariable UUID businessId, @PathVariable UUID id) {
    service.revokeCredential(businessId, id);
    return ApiResponse.ok(null);
  }

  @GetMapping("/webhook-endpoints")
  @RequireBusinessPermission(value = MANAGE_CREDENTIALS, businessIdParam = "businessId")
  public ApiResponse<List<WebhookEndpointResponse>> listWebhooks(@PathVariable UUID businessId) {
    return ApiResponse.ok(service.listWebhooks(businessId));
  }

  @PostMapping("/webhook-endpoints")
  @ResponseStatus(HttpStatus.CREATED)
  @RequireBusinessPermission(value = MANAGE_CREDENTIALS, businessIdParam = "businessId")
  public ApiResponse<WebhookEndpointCreatedResponse> registerWebhook(
      @PathVariable UUID businessId,
      @Valid @RequestBody RegisterWebhookEndpointRequest request) {
    return ApiResponse.ok(service.registerWebhook(businessId, request));
  }

  @DeleteMapping("/webhook-endpoints/{id}")
  @RequireBusinessPermission(value = MANAGE_CREDENTIALS, businessIdParam = "businessId")
  public ApiResponse<Void> deactivateWebhook(
      @PathVariable UUID businessId, @PathVariable UUID id) {
    service.deactivateWebhook(businessId, id);
    return ApiResponse.ok(null);
  }
}
