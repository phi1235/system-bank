package com.banksystem.auth.api;

import com.banksystem.auth.api.dto.AuthDtos.LoginRequest;
import com.banksystem.auth.api.dto.AuthDtos.LoginResponse;
import com.banksystem.auth.api.dto.AuthDtos.MfaEnableRequest;
import com.banksystem.auth.api.dto.AuthDtos.MfaSetupResponse;
import com.banksystem.auth.api.dto.AuthDtos.MfaVerifyRequest;
import com.banksystem.auth.api.dto.AuthDtos.RefreshRequest;
import com.banksystem.auth.api.dto.AuthDtos.RegisterRequest;
import com.banksystem.auth.api.dto.AuthDtos.RegisterResponse;
import com.banksystem.auth.api.dto.AuthDtos.SessionResponse;
import com.banksystem.auth.api.dto.AuthDtos.TokenResponse;
import com.banksystem.auth.api.dto.AuthDtos.UserMeResponse;
import com.banksystem.auth.application.AuthService;
import com.banksystem.auth.application.SessionService;
import com.banksystem.auth.config.UserPrincipal;
import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;
  private final SessionService sessionService;

  public AuthController(AuthService authService, SessionService sessionService) {
    this.authService = authService;
    this.sessionService = sessionService;
  }

  @PostMapping("/register")
  public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest req) {
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(authService.register(req)));
  }

  @PostMapping("/login")
  public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
    return ApiResponse.ok(authService.login(req, UserContext.clientIp(http), UserContext.userAgent(http)));
  }

  @PostMapping("/mfa/verify")
  public ApiResponse<TokenResponse> verifyMfa(@Valid @RequestBody MfaVerifyRequest req, HttpServletRequest http) {
    return ApiResponse.ok(authService.verifyMfa(req, UserContext.clientIp(http), UserContext.userAgent(http)));
  }

  @PostMapping("/refresh")
  public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest req, HttpServletRequest http) {
    return ApiResponse.ok(authService.refresh(req, UserContext.clientIp(http), UserContext.userAgent(http)));
  }

  @PostMapping("/logout")
  public ApiResponse<Map<String, String>> logout(
      @RequestBody(required = false) RefreshRequest body,
      HttpServletRequest http) {
    String auth = http.getHeader(HttpHeaders.AUTHORIZATION);
    String access = auth != null && auth.startsWith("Bearer ") ? auth.substring(7) : null;
    String refresh = body != null ? body.refreshToken() : null;
    authService.logout(access, refresh);
    return ApiResponse.ok(Map.of("status", "ok"));
  }

  @GetMapping("/sessions")
  public ApiResponse<List<SessionResponse>> listSessions(
      @AuthenticationPrincipal UserPrincipal principal,
      HttpServletRequest http) {
    String currentJti = currentRefreshJti(http);
    return ApiResponse.ok(sessionService.listSessions(principal.userId(), currentJti));
  }

  @DeleteMapping("/sessions/{id}")
  public ApiResponse<Map<String, String>> revokeSession(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable("id") String id,
      HttpServletRequest http) {
    sessionService.revoke(principal.userId(), id, currentRefreshJti(http));
    return ApiResponse.ok(Map.of("status", "ok"));
  }

  @PostMapping("/sessions/revoke-others")
  public ApiResponse<Map<String, Object>> revokeOtherSessions(
      @AuthenticationPrincipal UserPrincipal principal,
      HttpServletRequest http) {
    int count = sessionService.revokeOthers(principal.userId(), currentRefreshJti(http));
    return ApiResponse.ok(Map.of("status", "ok", "revoked", count));
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

  /**
   * Optional header so the client can mark which refresh session is "this device".
   * Header: X-Refresh-Token: &lt;refresh jwt&gt;
   */
  private String currentRefreshJti(HttpServletRequest request) {
    return sessionService.resolveRefreshJti(request.getHeader("X-Refresh-Token"));
  }
}
