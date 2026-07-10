package com.banksystem.auth.application;

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
import com.banksystem.auth.domain.AuthAuditLogEntity;
import com.banksystem.auth.domain.AuthAuditLogRepository;
import com.banksystem.auth.domain.UserEntity;
import com.banksystem.auth.domain.UserRepository;
import com.banksystem.auth.infrastructure.jwt.JwtService;
import com.banksystem.auth.infrastructure.jwt.JwtService.TokenPair;
import com.banksystem.auth.infrastructure.redis.TokenStore;
import com.banksystem.auth.infrastructure.security.BoundPasswordEncoder;
import com.banksystem.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private final UserRepository userRepository;
  private final AuthAuditLogRepository auditLogRepository;
  private final BoundPasswordEncoder boundPasswordEncoder;
  private final JwtService jwtService;
  private final TokenStore tokenStore;
  private final MfaService mfaService;
  private final RbacService rbacService;
  private final int maxFailures;
  private final int lockMinutes;

  public AuthService(
      UserRepository userRepository,
      AuthAuditLogRepository auditLogRepository,
      BoundPasswordEncoder boundPasswordEncoder,
      JwtService jwtService,
      TokenStore tokenStore,
      MfaService mfaService,
      RbacService rbacService,
      @Value("${bank.security.login-max-failures:5}") int maxFailures,
      @Value("${bank.security.login-lock-minutes:15}") int lockMinutes) {
    this.userRepository = userRepository;
    this.auditLogRepository = auditLogRepository;
    this.boundPasswordEncoder = boundPasswordEncoder;
    this.jwtService = jwtService;
    this.tokenStore = tokenStore;
    this.mfaService = mfaService;
    this.rbacService = rbacService;
    this.maxFailures = maxFailures;
    this.lockMinutes = lockMinutes;
  }

  @Transactional
  public RegisterResponse register(RegisterRequest req) {
    validatePassword(req.password());
    String username = BoundPasswordEncoder.normalizeUsername(req.username());
    if (username.isBlank() || username.length() < 3) {
      throw new BusinessException("INVALID_USERNAME", "Username must be at least 3 characters",
          HttpStatus.BAD_REQUEST);
    }
    if (userRepository.existsByUsername(username)) {
      throw new BusinessException("USERNAME_TAKEN", "Username already taken", HttpStatus.CONFLICT);
    }
    if (userRepository.existsByEmail(req.email())) {
      throw new BusinessException("EMAIL_TAKEN", "Email already taken", HttpStatus.CONFLICT);
    }

    UserEntity user = new UserEntity();
    user.setId(UUID.randomUUID());
    user.setUsername(username);
    user.setEmail(req.email().trim().toLowerCase());
    // BCrypt(HMAC(password, pepper) bound to username) — not reversible; not raw password
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
  public LoginResponse login(LoginRequest req, String ip) {
    if (tokenStore.getLoginFail(ip) >= maxFailures) {
      throw new BusinessException("LOGIN_LOCKED",
          "Too many failed attempts. Try again later.", HttpStatus.TOO_MANY_REQUESTS);
    }

    String username = BoundPasswordEncoder.normalizeUsername(req.username());
    UserEntity user = userRepository.findByUsername(username).orElse(null);
    if (user == null
        || !boundPasswordEncoder.matches(req.password(), user.getUsername(), user.getPasswordHash())) {
      long fails = tokenStore.incrementLoginFail(ip, lockMinutes);
      audit(null, "LOGIN_FAILED", ip, "username=" + username + ",fails=" + fails);
      if (fails >= maxFailures) {
        throw new BusinessException("LOGIN_LOCKED",
            "Too many failed attempts. Try again later.", HttpStatus.TOO_MANY_REQUESTS);
      }
      throw new BusinessException("INVALID_CREDENTIALS", "Invalid username or password",
          HttpStatus.UNAUTHORIZED);
    }
    if (!user.isEnabled()) {
      throw new BusinessException("USER_DISABLED", "Account is disabled", HttpStatus.FORBIDDEN);
    }

    tokenStore.clearLoginFail(ip);

    if (user.isMfaEnabled()) {
      String mfaToken = jwtService.issueMfaToken(user);
      audit(user.getId(), "LOGIN_MFA_REQUIRED", ip, null);
      return LoginResponse.mfaRequired(mfaToken);
    }

    TokenPair pair = issueAndStore(user);
    audit(user.getId(), "LOGIN_SUCCESS", ip, null);
    return LoginResponse.tokens(toTokenResponse(pair));
  }

  @Transactional(readOnly = true)
  public TokenResponse verifyMfa(MfaVerifyRequest req, String ip) {
    Claims claims;
    try {
      claims = jwtService.parse(req.mfaToken());
    } catch (Exception e) {
      throw new BusinessException("INVALID_MFA_TOKEN", "Invalid or expired MFA token",
          HttpStatus.UNAUTHORIZED);
    }
    if (!jwtService.isType(claims, JwtService.TYPE_MFA)) {
      throw new BusinessException("INVALID_MFA_TOKEN", "Token is not MFA pending",
          HttpStatus.UNAUTHORIZED);
    }
    UUID userId = UUID.fromString(claims.getSubject());
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found",
            HttpStatus.NOT_FOUND));
    if (!mfaService.verifyUserCode(userId, req.code())) {
      audit(userId, "MFA_VERIFY_FAILED", ip, null);
      throw new BusinessException("INVALID_MFA_CODE", "Invalid MFA code", HttpStatus.UNAUTHORIZED);
    }
    TokenPair pair = issueAndStore(user);
    audit(userId, "MFA_VERIFY_SUCCESS", ip, null);
    return toTokenResponse(pair);
  }

  @Transactional(readOnly = true)
  public TokenResponse refresh(RefreshRequest req) {
    Claims claims;
    try {
      claims = jwtService.parse(req.refreshToken());
    } catch (Exception e) {
      throw new BusinessException("INVALID_REFRESH", "Invalid refresh token", HttpStatus.UNAUTHORIZED);
    }
    if (!jwtService.isType(claims, JwtService.TYPE_REFRESH)) {
      throw new BusinessException("INVALID_REFRESH", "Not a refresh token", HttpStatus.UNAUTHORIZED);
    }
    String jti = claims.getId();
    if (tokenStore.isBlacklisted(jti)) {
      throw new BusinessException("TOKEN_REVOKED", "Refresh token revoked", HttpStatus.UNAUTHORIZED);
    }
    UUID userId = tokenStore.getRefreshUser(jti)
        .orElseThrow(() -> new BusinessException("INVALID_REFRESH", "Refresh session not found",
            HttpStatus.UNAUTHORIZED));

    // rotate
    tokenStore.deleteRefresh(jti);
    tokenStore.blacklist(jti, jwtService.remainingTtlSeconds(claims));

    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found",
            HttpStatus.NOT_FOUND));
    TokenPair pair = issueAndStore(user);
    audit(userId, "TOKEN_REFRESH", null, null);
    return toTokenResponse(pair);
  }

  public void logout(String accessToken) {
    if (accessToken == null || accessToken.isBlank()) {
      return;
    }
    try {
      Claims claims = jwtService.parse(accessToken);
      String jti = claims.getId();
      tokenStore.blacklist(jti, jwtService.remainingTtlSeconds(claims));
      // best-effort: if client also sends refresh later it won't work after rotate
      UUID userId = UUID.fromString(claims.getSubject());
      audit(userId, "LOGOUT", null, "jti=" + jti);
    } catch (Exception ignored) {
      // already invalid
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
    List<String> permissions = rbacService.resolvePermissions(roles);
    return new UserMeResponse(
        user.getId().toString(),
        user.getUsername(),
        user.getEmail(),
        roles,
        permissions,
        user.isMfaEnabled(),
        rbacService.isStaff(roles)
    );
  }

  public UserEntity requireUser(UUID userId) {
    return userRepository.findById(userId)
        .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found",
            HttpStatus.NOT_FOUND));
  }

  private TokenPair issueAndStore(UserEntity user) {
    TokenPair pair = jwtService.issueSessionTokens(user);
    tokenStore.storeRefresh(pair.refreshJti(), user.getId(), pair.refreshTtlSeconds());
    return pair;
  }

  private TokenResponse toTokenResponse(TokenPair pair) {
    return new TokenResponse(
        pair.accessToken(),
        pair.refreshToken(),
        "Bearer",
        pair.accessTtlSeconds(),
        false
    );
  }

  private void validatePassword(String password) {
    if (password == null || password.length() < 8
        || !password.matches(".*[A-Za-z].*")
        || !password.matches(".*\\d.*")) {
      throw new BusinessException("WEAK_PASSWORD",
          "Password must be at least 8 characters with letters and numbers",
          HttpStatus.BAD_REQUEST);
    }
  }

  private void audit(UUID userId, String action, String ip, String detail) {
    auditLogRepository.save(AuthAuditLogEntity.of(userId, action, ip, detail));
  }
}
