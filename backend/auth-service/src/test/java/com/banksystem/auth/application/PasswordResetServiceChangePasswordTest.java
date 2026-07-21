package com.banksystem.auth.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banksystem.auth.api.dto.PasswordResetDtos.ChangePasswordRequest;
import com.banksystem.auth.domain.AuthAuditLogRepository;
import com.banksystem.auth.domain.PasswordResetTicketRepository;
import com.banksystem.auth.domain.UserEntity;
import com.banksystem.auth.domain.UserRepository;
import com.banksystem.auth.infrastructure.security.BoundPasswordEncoder;
import com.banksystem.common.exception.BusinessException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PasswordResetServiceChangePasswordTest {

  private UserRepository userRepository;
  private SessionService sessionService;
  private AuthAuditLogRepository auditLogRepository;
  private BoundPasswordEncoder passwordEncoder;
  private PasswordResetService service;
  private final UUID userId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    sessionService = mock(SessionService.class);
    auditLogRepository = mock(AuthAuditLogRepository.class);
    passwordEncoder = new BoundPasswordEncoder("test-pepper-for-unit-tests-only");
    service = new PasswordResetService(
        userRepository,
        mock(PasswordResetTicketRepository.class),
        auditLogRepository,
        passwordEncoder,
        sessionService
    );
  }

  @Test
  void changePassword_updatesHashClearsFlagAndRevokesSessions() {
    UserEntity user = new UserEntity();
    user.setId(userId);
    user.setUsername("alice");
    user.setPasswordHash(passwordEncoder.encode("OldPass12", "alice"));
    user.setMustChangePassword(true);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(sessionService.revokeAll(userId)).thenReturn(2);

    service.changePassword(userId, new ChangePasswordRequest("OldPass12", "NewPass34"));

    assertFalse(user.isMustChangePassword());
    assertEquals(true, passwordEncoder.matches("NewPass34", "alice", user.getPasswordHash()));
    verify(userRepository).save(user);
    verify(sessionService).revokeAll(userId);
    verify(auditLogRepository).save(any());
  }

  @Test
  void changePassword_rejectsWrongCurrentPassword() {
    UserEntity user = new UserEntity();
    user.setId(userId);
    user.setUsername("alice");
    user.setPasswordHash(passwordEncoder.encode("OldPass12", "alice"));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    BusinessException ex = assertThrows(BusinessException.class,
        () -> service.changePassword(userId, new ChangePasswordRequest("wrong", "NewPass34")));
    assertEquals("INVALID_CREDENTIALS", ex.getCode());
    verify(sessionService, never()).revokeAll(any());
  }

  @Test
  void changePassword_rejectsSamePassword() {
    UserEntity user = new UserEntity();
    user.setId(userId);
    user.setUsername("alice");
    user.setPasswordHash(passwordEncoder.encode("SamePass1", "alice"));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    BusinessException ex = assertThrows(BusinessException.class,
        () -> service.changePassword(userId, new ChangePasswordRequest("SamePass1", "SamePass1")));
    assertEquals("WEAK_PASSWORD", ex.getCode());
    verify(sessionService, never()).revokeAll(eq(userId));
  }
}
