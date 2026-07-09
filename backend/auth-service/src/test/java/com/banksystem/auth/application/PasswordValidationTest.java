package com.banksystem.auth.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.banksystem.auth.api.dto.AuthDtos.RegisterRequest;
import com.banksystem.auth.domain.AuthAuditLogRepository;
import com.banksystem.auth.domain.UserRepository;
import com.banksystem.auth.infrastructure.jwt.JwtService;
import com.banksystem.auth.infrastructure.redis.TokenStore;
import com.banksystem.auth.infrastructure.security.BoundPasswordEncoder;
import com.banksystem.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PasswordValidationTest {

  private AuthService authService;

  @BeforeEach
  void setUp() {
    authService = new AuthService(
        mock(UserRepository.class),
        mock(AuthAuditLogRepository.class),
        new BoundPasswordEncoder("test-pepper-for-unit-tests-only"),
        mock(JwtService.class),
        mock(TokenStore.class),
        mock(MfaService.class),
        5,
        15
    );
  }

  @Test
  void weakPasswordRejected() {
    assertThrows(BusinessException.class, () ->
        authService.register(new RegisterRequest("user1", "a@b.com", "short", "Name")));
    assertThrows(BusinessException.class, () ->
        authService.register(new RegisterRequest("user1", "a@b.com", "allletters", "Name")));
    assertThrows(BusinessException.class, () ->
        authService.register(new RegisterRequest("user1", "a@b.com", "12345678", "Name")));
  }

  @Test
  void strongPasswordAcceptedAtValidationLayer() {
    UserRepository users = mock(UserRepository.class);
    org.mockito.Mockito.when(users.existsByUsername(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
    org.mockito.Mockito.when(users.existsByEmail(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
    org.mockito.Mockito.when(users.save(org.mockito.ArgumentMatchers.any())).thenAnswer(i -> i.getArgument(0));

    AuthService svc = new AuthService(
        users,
        mock(AuthAuditLogRepository.class),
        new BoundPasswordEncoder("test-pepper-for-unit-tests-only"),
        mock(JwtService.class),
        mock(TokenStore.class),
        mock(MfaService.class),
        5,
        15
    );
    assertDoesNotThrow(() ->
        svc.register(new RegisterRequest("user1", "a@b.com", "Secret123!", "Name")));
  }
}
