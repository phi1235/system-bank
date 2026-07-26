package com.banksystem.auth.application;

import com.banksystem.auth.api.dto.AuthDtos.SessionResponse;
import com.banksystem.auth.domain.AuthAuditLogEntity;
import com.banksystem.auth.domain.AuthAuditLogRepository;
import com.banksystem.auth.infrastructure.jwt.JwtService;
import com.banksystem.auth.infrastructure.redis.SessionStore;
import com.banksystem.auth.infrastructure.redis.SessionStore.SessionMeta;
import com.banksystem.auth.infrastructure.redis.TokenStore;
import com.banksystem.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SessionService {

  private final SessionStore sessionStore;
  private final TokenStore tokenStore;
  private final AuthAuditLogRepository auditLogRepository;
  private final JwtService jwtService;

  public SessionService(
      SessionStore sessionStore,
      TokenStore tokenStore,
      AuthAuditLogRepository auditLogRepository,
      JwtService jwtService) {
    this.sessionStore = sessionStore;
    this.tokenStore = tokenStore;
    this.auditLogRepository = auditLogRepository;
    this.jwtService = jwtService;
  }

  /**
   * Resolve the refresh-token JTI from a raw refresh JWT (or null when absent/invalid).
   * Lets the API layer mark which session is "this device" without touching JWT internals.
   */
  public String resolveRefreshJti(String refreshToken) {
    if (refreshToken == null || refreshToken.isBlank()) {
      return null;
    }
    try {
      Claims claims = jwtService.parse(refreshToken);
      if (!jwtService.isType(claims, JwtService.TYPE_REFRESH)) {
        return null;
      }
      return claims.getId();
    } catch (Exception ex) {
      return null;
    }
  }

  public void trackRefreshSession(
      String refreshJti,
      UUID userId,
      String ip,
      String userAgent,
      long refreshTtlSeconds) {
    Instant now = Instant.now();
    SessionMeta meta = new SessionMeta(
        refreshJti,
        userId,
        truncate(ip, 64),
        truncate(userAgent, 256),
        now,
        now.plusSeconds(Math.max(refreshTtlSeconds, 1))
    );
    sessionStore.save(meta, refreshTtlSeconds);
  }

  public List<SessionResponse> listSessions(UUID userId, String currentRefreshJti) {
    return sessionStore.listByUser(userId).stream()
        .map(meta -> toResponse(meta, currentRefreshJti))
        .toList();
  }

  public void revoke(UUID userId, String sessionId, String currentRefreshJti) {
    if (sessionId == null || sessionId.isBlank()) {
      throw new BusinessException("SESSION_NOT_FOUND", "Session not found", HttpStatus.NOT_FOUND);
    }
    if (sessionId.equals(currentRefreshJti)) {
      throw new BusinessException(
          "SESSION_CURRENT",
          "Cannot revoke the current session; use logout instead",
          HttpStatus.BAD_REQUEST);
    }
    SessionMeta meta = sessionStore.get(sessionId)
        .filter(s -> userId.equals(s.userId()))
        .orElseThrow(() -> new BusinessException(
            "SESSION_NOT_FOUND", "Session not found", HttpStatus.NOT_FOUND));

    revokeMeta(meta);
    audit(userId, "SESSION_REVOKE", meta.ip(), "jti=" + sessionId);
  }

  public int revokeOthers(UUID userId, String currentRefreshJti) {
    List<SessionMeta> sessions = sessionStore.listByUser(userId);
    int count = 0;
    for (SessionMeta meta : sessions) {
      if (currentRefreshJti != null && currentRefreshJti.equals(meta.jti())) {
        continue;
      }
      revokeMeta(meta);
      count++;
    }
    if (count > 0) {
      audit(userId, "SESSION_REVOKE_OTHERS", null, "count=" + count);
    }
    return count;
  }

  /**
   * Revoke every refresh session for the user (e.g. after password change).
   * Returns how many sessions were revoked.
   */
  public int revokeAll(UUID userId) {
    List<SessionMeta> sessions = sessionStore.listByUser(userId);
    for (SessionMeta meta : sessions) {
      revokeMeta(meta);
    }
    if (!sessions.isEmpty()) {
      audit(userId, "SESSION_REVOKE_ALL", null, "count=" + sessions.size());
    }
    return sessions.size();
  }

  /** Best-effort cleanup when the user logs out the current browser. */
  public void forget(UUID userId, String refreshJti) {
    if (refreshJti == null || refreshJti.isBlank()) {
      return;
    }
    sessionStore.delete(userId, refreshJti);
    tokenStore.deleteRefresh(refreshJti);
  }

  private void revokeMeta(SessionMeta meta) {
    tokenStore.deleteRefresh(meta.jti());
    long ttl = Math.max(Duration.between(Instant.now(), meta.expiresAt()).getSeconds(), 1);
    tokenStore.blacklist(meta.jti(), ttl);
    sessionStore.delete(meta.userId(), meta.jti());
  }

  private SessionResponse toResponse(SessionMeta meta, String currentRefreshJti) {
    boolean current = currentRefreshJti != null && currentRefreshJti.equals(meta.jti());
    return new SessionResponse(
        meta.jti(),
        meta.ip(),
        meta.userAgent(),
        meta.createdAt(),
        meta.expiresAt(),
        current
    );
  }

  private void audit(UUID userId, String action, String ip, String detail) {
    auditLogRepository.save(AuthAuditLogEntity.of(userId, action, ip, detail));
  }

  private static String truncate(String value, int max) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
  }
}
