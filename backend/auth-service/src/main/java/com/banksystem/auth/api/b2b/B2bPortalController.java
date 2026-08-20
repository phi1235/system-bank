package com.banksystem.auth.api.b2b;

import com.banksystem.auth.api.dto.B2bDtos.B2bClientCreateRequest;
import com.banksystem.auth.api.dto.B2bDtos.B2bClientFilterRequest;
import com.banksystem.auth.api.dto.B2bDtos.B2bClientResponse;
import com.banksystem.auth.api.dto.B2bDtos.B2bClientUpdateRequest;
import com.banksystem.auth.api.dto.B2bDtos.B2bConsentCreateRequest;
import com.banksystem.auth.api.dto.B2bDtos.B2bConsentFilterRequest;
import com.banksystem.auth.api.dto.B2bDtos.B2bConsentResponse;
import com.banksystem.auth.api.dto.B2bDtos.B2bMetricSummary;
import com.banksystem.auth.api.dto.B2bDtos.B2bSandboxExecuteRequest;
import com.banksystem.auth.api.dto.B2bDtos.B2bSandboxExecuteResponse;
import com.banksystem.auth.application.b2b.B2bAccountConsentService;
import com.banksystem.auth.application.b2b.B2bClientApplicationService;
import com.banksystem.auth.application.b2b.B2bMetricService;
import com.banksystem.auth.application.b2b.B2bSandboxService;
import com.banksystem.auth.application.b2b.query.B2bClientSearchQuery;
import com.banksystem.auth.application.b2b.query.B2bConsentSearchQuery;
import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.SecurityHeaders;
import com.banksystem.common.security.UserContext;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/b2b-portal")
public class B2bPortalController {

  private final B2bClientApplicationService clientService;
  private final B2bAccountConsentService consentService;
  private final B2bMetricService metricService;
  private final B2bSandboxService sandboxService;

  public B2bPortalController(
      B2bClientApplicationService clientService,
      B2bAccountConsentService consentService,
      B2bMetricService metricService,
      B2bSandboxService sandboxService) {
    this.clientService = clientService;
    this.consentService = consentService;
    this.metricService = metricService;
    this.sandboxService = sandboxService;
  }

  /* ── B2B Client Applications ── */

  @GetMapping("/clients")
  public ApiResponse<Page<B2bClientResponse>> listClients(@Valid @ModelAttribute B2bClientFilterRequest req) {
    UserContext.requireAnyPermission(
        SecurityHeaders.PERM_B2B_OPENBANKING_APPS_VIEW,
        SecurityHeaders.PERM_BUSINESS_DASHBOARD_VIEW,
        "ADMIN", "BUSINESS_OWNER", "BUSINESS_FINANCE", "BUSINESS_OPERATOR");
    B2bClientSearchQuery query = B2bClientSearchQuery.of(req);
    return ApiResponse.ok(clientService.listClients(query));
  }

  @GetMapping("/clients/{clientId}")
  public ApiResponse<B2bClientResponse> getClient(@PathVariable String clientId) {
    UserContext.requireAnyPermission(
        SecurityHeaders.PERM_B2B_OPENBANKING_APPS_VIEW,
        SecurityHeaders.PERM_BUSINESS_DASHBOARD_VIEW,
        "ADMIN", "BUSINESS_OWNER", "BUSINESS_FINANCE", "BUSINESS_OPERATOR");
    return ApiResponse.ok(clientService.getClient(clientId));
  }

  @PostMapping("/clients")
  public ResponseEntity<ApiResponse<B2bClientResponse>> createClient(@Valid @RequestBody B2bClientCreateRequest req) {
    UserContext.requireAnyPermission(
        SecurityHeaders.PERM_B2B_OPENBANKING_APPS_MANAGE,
        "ADMIN", "BUSINESS_OWNER");
    B2bClientResponse response = clientService.createClient(req);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
  }

  @PutMapping("/clients/{clientId}")
  public ApiResponse<B2bClientResponse> updateClient(
      @PathVariable String clientId,
      @Valid @RequestBody B2bClientUpdateRequest req) {
    UserContext.requireAnyPermission(
        SecurityHeaders.PERM_B2B_OPENBANKING_APPS_MANAGE,
        "ADMIN", "BUSINESS_OWNER");
    return ApiResponse.ok(clientService.updateClient(clientId, req));
  }

  @DeleteMapping("/clients/{clientId}")
  public ApiResponse<Void> deleteClient(@PathVariable String clientId) {
    UserContext.requireAnyPermission(
        SecurityHeaders.PERM_B2B_OPENBANKING_APPS_MANAGE,
        "ADMIN", "BUSINESS_OWNER");
    clientService.deleteClient(clientId);
    return ApiResponse.ok(null);
  }

  /* ── B2B Account Consents ── */

  @GetMapping("/consents")
  public ApiResponse<Page<B2bConsentResponse>> listConsents(@Valid @ModelAttribute B2bConsentFilterRequest req) {
    UserContext.requireAnyPermission(
        SecurityHeaders.PERM_B2B_OPENBANKING_CONSENTS_VIEW,
        SecurityHeaders.PERM_BUSINESS_DASHBOARD_VIEW,
        "ADMIN", "BUSINESS_OWNER", "BUSINESS_FINANCE", "BUSINESS_OPERATOR");
    B2bConsentSearchQuery query = B2bConsentSearchQuery.of(req);
    return ApiResponse.ok(consentService.listConsents(query));
  }

  @PostMapping("/consents")
  public ResponseEntity<ApiResponse<B2bConsentResponse>> grantConsent(@Valid @RequestBody B2bConsentCreateRequest req) {
    UserContext.requireAnyPermission(
        SecurityHeaders.PERM_B2B_OPENBANKING_CONSENTS_MANAGE,
        "ADMIN", "BUSINESS_OWNER");
    B2bConsentResponse response = consentService.grantConsent(req);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
  }

  @DeleteMapping("/consents/{consentId}")
  public ApiResponse<B2bConsentResponse> revokeConsent(@PathVariable UUID consentId) {
    UserContext.requireAnyPermission(
        SecurityHeaders.PERM_B2B_OPENBANKING_CONSENTS_MANAGE,
        "ADMIN", "BUSINESS_OWNER");
    return ApiResponse.ok(consentService.revokeConsent(consentId));
  }

  /* ── B2B Realtime Metrics & Sandbox Tester ── */

  @GetMapping("/metrics")
  public ApiResponse<List<B2bMetricSummary>> getMetrics() {
    UserContext.requireAnyPermission(
        SecurityHeaders.PERM_B2B_OPENBANKING_LOGS_VIEW,
        SecurityHeaders.PERM_BUSINESS_DASHBOARD_VIEW,
        "ADMIN", "BUSINESS_OWNER", "BUSINESS_FINANCE");
    return ApiResponse.ok(metricService.getMetrics());
  }

  @PostMapping("/sandbox/execute")
  public ApiResponse<B2bSandboxExecuteResponse> executeSandbox(@Valid @RequestBody B2bSandboxExecuteRequest req) {
    UserContext.requireAnyPermission(
        SecurityHeaders.PERM_B2B_OPENBANKING_SANDBOX_USE,
        SecurityHeaders.PERM_BUSINESS_DASHBOARD_VIEW,
        "ADMIN", "BUSINESS_OWNER", "BUSINESS_FINANCE", "BUSINESS_OPERATOR");
    return ApiResponse.ok(sandboxService.executeSimulation(req));
  }
}
