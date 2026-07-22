package com.banksystem.auth.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.banksystem.auth.api.dto.RbacDtos.StaffUserDto;
import com.banksystem.auth.domain.PasswordResetTicketRepository;
import com.banksystem.auth.domain.PermissionRepository;
import com.banksystem.auth.domain.RolePermissionRepository;
import com.banksystem.auth.domain.RoleRepository;
import com.banksystem.auth.domain.UserEntity;
import com.banksystem.auth.domain.UserRepository;
import com.banksystem.common.api.PageResponse;
import com.banksystem.common.exception.BusinessException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class RbacServiceListUsersTest {

  private UserRepository userRepository;
  private PasswordResetTicketRepository ticketRepository;
  private RolePermissionRepository rolePermissionRepository;
  private RbacService service;

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    ticketRepository = mock(PasswordResetTicketRepository.class);
    rolePermissionRepository = mock(RolePermissionRepository.class);
    service =
        new RbacService(
            mock(RoleRepository.class),
            mock(PermissionRepository.class),
            rolePermissionRepository,
            userRepository,
            ticketRepository);
  }

  @Test
  void listUsers_passesEnabledAndQFlags() {
    UserEntity u = sampleUser(true);
    when(userRepository.searchAdmin(
            eq(false),
            any(UUID.class),
            eq(true),
            eq(false),
            eq(true),
            eq("alice"),
            any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(u), PageRequest.of(0, 20), 1));
    when(rolePermissionRepository.findPermissionCodesByRoleCodes(any())).thenReturn(List.of());
    when(ticketRepository.existsByUserIdAndStatus(u.getId(), "OPEN")).thenReturn(false);

    PageResponse<StaffUserDto> page = service.listUsers(0, 20, " alice ", false, null);

    assertEquals(1, page.items().size());
    assertEquals("alice", page.items().get(0).username());
    assertEquals(u.getCreatedAt(), page.items().get(0).createdAt());
    assertTrue(page.items().get(0).enabled());
  }

  @Test
  void listUsers_rejectsInvalidUserId() {
    BusinessException ex =
        assertThrows(
            BusinessException.class, () -> service.listUsers(0, 20, null, null, "not-uuid"));
    assertEquals("INVALID_USER_ID", ex.getCode());
  }

  @Test
  void getUser_notFound() {
    UUID id = UUID.randomUUID();
    when(userRepository.findById(id)).thenReturn(Optional.empty());
    BusinessException ex = assertThrows(BusinessException.class, () -> service.getUser(id));
    assertEquals("USER_NOT_FOUND", ex.getCode());
  }

  private UserEntity sampleUser(boolean enabled) {
    UserEntity u = new UserEntity();
    u.setId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    u.setUsername("alice");
    u.setEmail("alice@example.com");
    u.setPasswordHash("x");
    u.setRoles("ADMIN");
    u.setEnabled(enabled);
    u.setCreatedAt(Instant.parse("2026-07-01T00:00:00Z"));
    u.setUpdatedAt(Instant.parse("2026-07-01T00:00:00Z"));
    return u;
  }
}
