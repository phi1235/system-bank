package com.banksystem.auth.api;

import com.banksystem.auth.api.dto.AuthDtos.LoginRequest;
import com.banksystem.auth.api.dto.AuthDtos.LoginResponse;
import com.banksystem.auth.api.dto.AuthDtos.MfaEnableRequest;
import com.banksystem.auth.api.dto.AuthDtos.MfaSetupResponse;
import com.banksystem.auth.api.dto.AuthDtos.MfaVerifyRequest;
import com.banksystem.auth.api.dto.AuthDtos.RefreshRequest;
import com.banksystem.auth.api.dto.AuthDtos.RegisterRequest;
import com.banksystem.auth.api.dto.AuthDtos.RegisterResponse;
import com.banksystem.auth.api.dto.AuthDtos.TokenResponse;
import com.banksystem.auth.api.dto.AuthDtos.UserMeResponse;
import com.banksystem.auth.application.AuthService;
import com.banksystem.auth.config.UserPrincipal;
import com.banksystem.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/register")
  public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(authService.register(req)));
  }

  @PostMapping("/login")
  public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
    return ApiResponse.ok(authService.login(req, clientIp(http)));
  }

  @PostMapping("/mfa/verify")
  public ApiResponse<TokenResponse> verifyMfa(@Valid @RequestBody MfaVerifyRequest req, HttpServletRequest http) {
    return ApiResponse.ok(authService.verifyMfa(req, clientIp(http)));
  }

  @PostMapping("/refresh")
  public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest req) {
    return ApiResponse.ok(authService.refresh(req));
  }

  @PostMapping("/logout")
  public ApiResponse<Map<String, String>> logout(HttpServletRequest http) {
    String auth = http.getHeader(HttpHeaders.AUTHORIZATION);
    String token = auth != null && auth.startsWith("Bearer ") ? auth.substring(7) : null;
    authService.logout(token);
    return ApiResponse.ok(Map.of("status", "ok"));
  }

  @PostMapping("/mfa/setup")
  public ApiResponse<MfaSetupResponse> mfaSetup(@AuthenticationPrincipal UserPrincipal principal) {
    return ApiResponse.ok(authService.setupMfa(principal.userId()));
  }

  @PostMapping("/mfa/enable")
  public ApiResponse<Map<String, Object>> mfaEnable(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody MfaEnableRequest req) {
    authService.enableMfa(principal.userId(), req);
    return ApiResponse.ok(Map.of("mfaEnabled", true));
  }

  @GetMapping("/me")
  public ApiResponse<UserMeResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
    return ApiResponse.ok(authService.me(principal.userId()));
  }

  private String clientIp(HttpServletRequest request) {
    String xff = request.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
      return xff.split(",")[0].trim();
    }
    return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
  }
}
