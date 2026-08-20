package com.banksystem.auth.api.b2b;

import com.banksystem.auth.api.dto.B2bDtos.B2bConsentCheckRequest;
import com.banksystem.auth.application.b2b.B2bAccountConsentService;
import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.RequireInternalApiKey;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/b2b/consents")
public class InternalB2bConsentController {

  private final B2bAccountConsentService consentService;

  public InternalB2bConsentController(B2bAccountConsentService consentService) {
    this.consentService = consentService;
  }

  @GetMapping("/verify")
  @RequireInternalApiKey
  public ApiResponse<Map<String, Object>> verifyConsent(@Valid @ModelAttribute B2bConsentCheckRequest req) {
    boolean authorized = consentService.verifyAccountAccess(req.clientId(), req.accountNumber(), req.permission());
    return ApiResponse.ok(Map.of(
        "clientId", req.clientId(),
        "accountNumber", req.accountNumber(),
        "permission", req.permission() == null ? "" : req.permission(),
        "authorized", authorized
    ));
  }
}
