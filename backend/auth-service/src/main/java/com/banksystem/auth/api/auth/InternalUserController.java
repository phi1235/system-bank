package com.banksystem.auth.api.auth;

import com.banksystem.auth.api.dto.AuthDtos.InternalUserCountsResponse;
import com.banksystem.auth.api.dto.AuthDtos.VerifyTotpRequest;
import com.banksystem.auth.api.dto.AuthDtos.VerifyTotpResponse;
import com.banksystem.auth.application.auth.InternalUserQueryService;
import com.banksystem.auth.application.auth.MfaService;
import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.RequireInternalApiKey;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/users")
@RequireInternalApiKey
public class InternalUserController {

  private final InternalUserQueryService queryService;
  private final MfaService mfaService;

  public InternalUserController(
      InternalUserQueryService queryService,
      MfaService mfaService) {
    this.queryService = queryService;
    this.mfaService = mfaService;
  }

  @GetMapping("/counts")
  public ApiResponse<InternalUserCountsResponse> counts() {
    return ApiResponse.ok(queryService.counts());
  }

  @PostMapping("/{userId}/verify-totp")
  public ApiResponse<VerifyTotpResponse> verifyTotp(
      @PathVariable UUID userId,
      @Valid @RequestBody VerifyTotpRequest request) {
    boolean valid = mfaService.verifyUserCode(userId, request.code());
    return ApiResponse.ok(new VerifyTotpResponse(valid));
  }
}
