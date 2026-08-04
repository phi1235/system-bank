package com.banksystem.auth.application.auth.impl;
import static com.banksystem.auth.api.dto.AuthDtos.*;
import static com.banksystem.auth.api.dto.PasswordResetDtos.*;
import static com.banksystem.auth.api.dto.RbacDtos.*;
import com.banksystem.auth.application.auth.*;
import com.banksystem.auth.application.rbac.*;
import com.banksystem.auth.domain.auth.*;
import com.banksystem.auth.domain.rbac.*;
import com.banksystem.auth.api.dto.*;

import com.banksystem.auth.api.dto.AuthDtos.LoginRequest;
import com.banksystem.auth.api.dto.AuthDtos.LoginResponse;
import com.banksystem.auth.api.dto.AuthDtos.MfaEnableRequest;
import com.banksystem.auth.api.dto.AuthDtos.MfaSetupResponse;
import com.banksystem.auth.api.dto.AuthDtos.MfaVerifyRequest;
import com.banksystem.auth.api.dto.AuthDtos.RefreshRequest;
import com.banksystem.auth.api.dto.AuthDtos.RegisterRequest;
import com.banksystem.auth.api.dto.AuthDtos.RegisterResponse;
import com.banksystem.auth.api.dto.AuthDtos.TokenResponse;
import com.banksystem.auth.infrastructure.jwt.JwtService;
import com.banksystem.auth.infrastructure.jwt.JwtService.TokenPair;
import com.banksystem.auth.infrastructure.redis.TokenStore;
import com.banksystem.auth.infrastructure.security.BoundPasswordEncoder;
import com.banksystem.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  private final UserRepository userRepository;
  private final AuthAuditLogRepository auditLogRepository;
  private final BoundPasswordEncoder boundPasswordEncoder;
  private final JwtService jwtService;
  private final TokenStore tokenStore;
  private final MfaService mfaService;
  private final SessionService sessionService;
  private final PermissionResolver permissionResolver;
  private final int maxFailures;
  private final long lockMinutes;

  public AuthServiceImpl(
      UserRepository userRepository,
      AuthAuditLogRepository auditLogRepository,
      BoundPasswordEncoder boundPasswordEncoder,
      JwtService jwtService,
      TokenStore tokenStore,
      MfaService mfaService,
      SessionService sessionService,
      PermissionResolver permissionResolver,
      @Value("${bank.login.max-failures:5}") int maxFailures,
      @Value("${bank.login.lock-minutes:15}") long lockMinutes) {
    this.userRepository = userRepository;
    this.auditLogRepository = auditLogRepository;
    this.boundPasswordEncoder = boundPasswordEncoder;
    this.jwtService = jwtService;
    this.tokenStore = tokenStore;
    this.mfaService = mfaService;
    this.sessionService = sessionService;
    this.permissionResolver = permissionResolver;
    this.maxFailures = maxFailures;
    this.lockMinutes = lockMinutes;
  }

  @Transactional
  public RegisterResponse register(RegisterRequest req) {
    validatePassword(req.password());
    String username = BoundPasswordEncoder.normalizeUsername(req.username());
    if (username.isBlank() || username.length() < 3) {
      throw new BusinessException("INVALID_USERNAME", "Username must be at least 3 characters");
    }
    if (userRepository.existsByUsername(username)) {
      throw new BusinessException("USERNAME_TAKEN", "Username already taken");
    }
    if (userRepository.existsByEmail(req.email())) {
      throw new BusinessException("EMAIL_TAKEN", "Email already taken");
    }

    UserEntity user = new UserEntity();
    user.setId(UUID.randomUUID());
    user.setUsername(username);
    user.setEmail(req.email().trim().toLowerCase());
    user.setPasswordHash(boundPasswordEncoder.encode(req.password(), username));
    user.setRoles("CUSTOMER");
    user.setEnabled(true);
    user.setMfaEnabled(false);
    user.setCreatedAt(Instant.now());
    user.setUpdatedAt(Instant.now());
    userRepository.save(user);
    audit(user.getId(), "REGISTER", null, "username=" + user.getUsername());
    return new RegisterResponse(user.getId().toString(), user.getUsername());
  }

  @Transactional(readOnly = true)
  public LoginResponse login(LoginRequest req, String ip, String userAgent) {
    if (tokenStore.getLoginFail(ip) >= maxFailures) {
      throw new BusinessException("LOGIN_LOCKED",
          "Too many failed attempts. Try again later.");
    }

    String username = BoundPasswordEncoder.normalizeUsername(req.username());
    UserEntity user = userRepository.findByUsername(username).orElse(null);
    if (user == null
        || !boundPasswordEncoder.matches(req.password(), user.getUsername(), user.getPasswordHash())) {
      long fails = tokenStore.incrementLoginFail(ip, lockMinutes);
      audit(null, "LOGIN_FAILED", ip, "username=" + username + ",fails=" + fails);
      if (fails >= maxFailures) {
        throw new BusinessException("LOGIN_LOCKED",
            "Too many failed attempts. Try again later.");
      }
      throw new BusinessException("INVALID_CREDENTIALS", "Invalid username or password");
    }
    if (!user.isEnabled()) {
      throw new BusinessException("USER_DISABLED", "Account is disabled");
    }

    tokenStore.clearLoginFail(ip);

    if (user.isMfaEnabled()) {
      String mfaToken = jwtService.issueMfaToken(user);
      audit(user.getId(), "LOGIN_MFA_REQUIRED", ip, null);
      return LoginResponse.mfaRequired(mfaToken);
    }

    TokenPair pair = issueAndStore(user, ip, userAgent);
    audit(user.getId(), "LOGIN_SUCCESS", ip, null);
    return LoginResponse.tokens(toTokenResponse(pair, user.isMustChangePassword()), user.isMustChangePassword());
  }

  @Transactional(readOnly = true)
  public TokenResponse verifyMfa(MfaVerifyRequest req, String ip, String userAgent) {
    Claims claims;
    try {
      claims = jwtService.parse(req.mfaToken());
    } catch (Exception e) {
      throw new BusinessException("INVALID_MFA_TOKEN", "Invalid or expired MFA token");
    }
    if (!jwtService.isType(claims, JwtService.TYPE_MFA)) {
      throw new BusinessException("INVALID_MFA_TOKEN", "Token is not MFA pending");
    }
    UUID userId = UUID.fromString(claims.getSubject());
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found"));
    if (!mfaService.verifyUserCode(userId, req.code())) {
      audit(userId, "MFA_VERIFY_FAILED", ip, null);
      throw new BusinessException("INVALID_MFA_CODE", "Invalid MFA code");
    }
    TokenPair pair = issueAndStore(user, ip, userAgent);
    audit(userId, "MFA_VERIFY_SUCCESS", ip, null);
    return toTokenResponse(pair, user.isMustChangePassword());
  }

  @Transactional(readOnly = true)
  public TokenResponse refresh(RefreshRequest req, String ip, String userAgent) {
    Claims claims;
    try {
      claims = jwtService.parse(req.refreshToken());
    } catch (Exception e) {
      throw new BusinessException("INVALID_REFRESH", "Invalid refresh token");
    }
    if (!jwtService.isType(claims, JwtService.TYPE_REFRESH)) {
      throw new BusinessException("INVALID_REFRESH", "Not a refresh token");
    }
    String jti = claims.getId();
    if (tokenStore.isBlacklisted(jti)) {
      throw new BusinessException("TOKEN_REVOKED", "Refresh token revoked");
    }
    UUID userId = tokenStore.getRefreshUser(jti)
        .orElseThrow(() -> new BusinessException("INVALID_REFRESH", "Refresh session not found"));

    tokenStore.deleteRefresh(jti);
    tokenStore.blacklist(jti, jwtService.remainingTtlSeconds(claims));
    sessionService.forget(userId, jti);

    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found"));
    TokenPair pair = issueAndStore(user, ip, userAgent);
    audit(userId, "TOKEN_REFRESH", ip, null);
    return toTokenResponse(pair, user.isMustChangePassword());
  }

  public void logout(String accessToken, String refreshToken) {
    UUID userId = null;
    if (accessToken != null && !accessToken.isBlank()) {
      try {
        Claims claims = jwtService.parse(accessToken);
        String jti = claims.getId();
        tokenStore.blacklist(jti, jwtService.remainingTtlSeconds(claims));
        userId = UUID.fromString(claims.getSubject());
        audit(userId, "LOGOUT", null, "jti=" + jti);
      } catch (Exception ignored) {
      }
    }
    if (refreshToken != null && !refreshToken.isBlank()) {
      try {
        Claims refreshClaims = jwtService.parse(refreshToken);
        if (jwtService.isType(refreshClaims, JwtService.TYPE_REFRESH)) {
          UUID refreshUser = UUID.fromString(refreshClaims.getSubject());
          String refreshJti = refreshClaims.getId();
          sessionService.forget(refreshUser, refreshJti);
          tokenStore.blacklist(refreshJti, jwtService.remainingTtlSeconds(refreshClaims));
          if (userId == null) {
            userId = refreshUser;
            audit(userId, "LOGOUT", null, "refreshJti=" + refreshJti);
          }
        }
      } catch (Exception ignored) {
      }
    }
  }

  public MfaSetupResponse setupMfa(UUID userId) {
    UserEntity user = requireUser(userId);
    var result = mfaService.setup(user);
    audit(userId, "MFA_SETUP", null, null);
    return new MfaSetupResponse(result.otpauthUri(), result.secret());
  }

  @Transactional
  public void enableMfa(UUID userId, MfaEnableRequest req) {
    UserEntity user = requireUser(userId);
    mfaService.enable(user, req.code());
    audit(userId, "MFA_ENABLED", null, null);
  }

  @Transactional(readOnly = true)
  public UserMeResponse me(UUID userId) {
    UserEntity user = requireUser(userId);
    List<String> roles = user.roleList();
    List<String> permissions = permissionResolver.resolvePermissions(roles);
    return new UserMeResponse(
        user.getId().toString(),
        user.getUsername(),
        user.getEmail(),
        roles,
        permissions,
        user.isMfaEnabled(),
        permissionResolver.isStaff(roles),
        user.isMustChangePassword(),
        user.isEnabled()
    );
  }

  public UserEntity requireUser(UUID userId) {
    return userRepository.findById(userId)
        .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found"));
  }

  private TokenPair issueAndStore(UserEntity user, String ip, String userAgent) {
    TokenPair pair = jwtService.issueSessionTokens(user);
    tokenStore.storeRefresh(pair.refreshJti(), user.getId(), pair.refreshTtlSeconds());
    sessionService.trackRefreshSession(
        pair.refreshJti(), user.getId(), ip, userAgent, pair.refreshTtlSeconds());
    return pair;
  }

  private TokenResponse toTokenResponse(TokenPair pair, boolean mustChangePassword) {
    return new TokenResponse(
        pair.accessToken(),
        pair.refreshToken(),
        "Bearer",
        pair.accessTtlSeconds(),
        false,
        mustChangePassword
    );
  }

  private void validatePassword(String password) {
    if (password == null || password.length() < 8
        || !password.matches(".*[A-Za-z].*")
        || !password.matches(".*\\d.*")) {
      throw new BusinessException("WEAK_PASSWORD",
          "Password must be at least 8 characters with letters and numbers");
    }
  }

  private void audit(UUID userId, String action, String ip, String detail) {
    auditLogRepository.save(AuthAuditLogEntity.of(userId, action, ip, detail));
  }
}
