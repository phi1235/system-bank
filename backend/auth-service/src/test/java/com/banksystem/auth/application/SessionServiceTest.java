package com.banksystem.auth.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banksystem.auth.api.dto.AuthDtos.SessionResponse;
import com.banksystem.auth.domain.AuthAuditLogRepository;
import com.banksystem.auth.infrastructure.jwt.JwtService;
import com.banksystem.auth.infrastructure.redis.SessionStore;
import com.banksystem.auth.infrastructure.redis.SessionStore.SessionMeta;
import com.banksystem.auth.infrastructure.redis.TokenStore;
import com.banksystem.common.exception.BusinessException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SessionServiceTest {

  private SessionStore sessionStore;
  private TokenStore tokenStore;
  private AuthAuditLogRepository auditLogRepository;
  private JwtService jwtService;
  private SessionService service;
  private final UUID userId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

  @BeforeEach
  void setUp() {
    sessionStore = mock(SessionStore.class);
    tokenStore = mock(TokenStore.class);
    auditLogRepository = mock(AuthAuditLogRepository.class);
    jwtService = mock(JwtService.class);
    service = new SessionService(sessionStore, tokenStore, auditLogRepository, jwtService);
  }

  @Test
  void listSessions_marksCurrent() {
    Instant now = Instant.now();
    when(sessionStore.listByUser(userId)).thenReturn(List.of(
        new SessionMeta("jti-1", userId, "1.1.1.1", "Chrome", now, now.plusSeconds(3600)),
        new SessionMeta("jti-2", userId, "2.2.2.2", "Firefox", now.minusSeconds(60), now.plusSeconds(3600))
    ));

    List<SessionResponse> list = service.listSessions(userId, "jti-1");

    assertEquals(2, list.size());
    assertTrue(list.get(0).current());
    assertEquals("jti-1", list.get(0).id());
    assertEquals(false, list.get(1).current());
  }

  @Test
  void revoke_rejectsCurrentSession() {
    Instant now = Instant.now();
    when(sessionStore.get("jti-1")).thenReturn(Optional.of(
        new SessionMeta("jti-1", userId, "1.1.1.1", "Chrome", now, now.plusSeconds(3600))));

    BusinessException ex = assertThrows(BusinessException.class,
        () -> service.revoke(userId, "jti-1", "jti-1"));
    assertEquals("SESSION_CURRENT", ex.getCode());
    verify(tokenStore, never()).deleteRefresh(any());
  }

  @Test
  void revoke_deletesAndBlacklistsOtherSession() {
    Instant now = Instant.now();
    when(sessionStore.get("jti-2")).thenReturn(Optional.of(
        new SessionMeta("jti-2", userId, "2.2.2.2", "Firefox", now, now.plusSeconds(1200))));

    service.revoke(userId, "jti-2", "jti-1");

    verify(tokenStore).deleteRefresh("jti-2");
    verify(tokenStore).blacklist(eq("jti-2"), anyLong());
    verify(sessionStore).delete(userId, "jti-2");
    verify(auditLogRepository).save(any());
  }

  @Test
  void revokeOthers_keepsCurrent() {
    Instant now = Instant.now();
    when(sessionStore.listByUser(userId)).thenReturn(List.of(
        new SessionMeta("jti-1", userId, "1.1.1.1", "Chrome", now, now.plusSeconds(3600)),
        new SessionMeta("jti-2", userId, "2.2.2.2", "Firefox", now, now.plusSeconds(3600)),
        new SessionMeta("jti-3", userId, "3.3.3.3", "Safari", now, now.plusSeconds(3600))
    ));

    int n = service.revokeOthers(userId, "jti-1");

    assertEquals(2, n);
    verify(tokenStore).deleteRefresh("jti-2");
    verify(tokenStore).deleteRefresh("jti-3");
    verify(tokenStore, never()).deleteRefresh("jti-1");
    verify(sessionStore).delete(userId, "jti-2");
    verify(sessionStore).delete(userId, "jti-3");
  }

  @Test
  void revokeAll_revokesEverySession() {
    Instant now = Instant.now();
    when(sessionStore.listByUser(userId)).thenReturn(List.of(
        new SessionMeta("jti-1", userId, "1.1.1.1", "Chrome", now, now.plusSeconds(3600)),
        new SessionMeta("jti-2", userId, "2.2.2.2", "Firefox", now, now.plusSeconds(3600))
    ));

    int n = service.revokeAll(userId);

    assertEquals(2, n);
    verify(tokenStore).deleteRefresh("jti-1");
    verify(tokenStore).deleteRefresh("jti-2");
    verify(sessionStore).delete(userId, "jti-1");
    verify(sessionStore).delete(userId, "jti-2");
    verify(auditLogRepository).save(any());
  }
}
