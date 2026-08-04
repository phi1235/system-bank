package com.banksystem.auth.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.banksystem.auth.api.dto.RbacDtos.StaffUserDto;
import com.banksystem.auth.application.mapper.RbacMapper;
import com.banksystem.auth.application.permission.PermissionResolver;
import com.banksystem.auth.application.query.RbacQueryService;
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
  private RbacQueryService service;

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    ticketRepository = mock(PasswordResetTicketRepository.class);
    rolePermissionRepository = mock(RolePermissionRepository.class);
    PermissionResolver permissionResolver = new PermissionResolver(rolePermissionRepository);
    RbacMapper mapper = new RbacMapper(rolePermissionRepository, permissionResolver);
    service =
        new RbacQueryService(
            mock(RoleRepository.class),
            mock(PermissionRepository.class),
            rolePermissionRepository,
            userRepository,
            ticketRepository,
            mapper);
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
    when(ticketRepository.existsByUserIdAndStatus(u.getId(), "OPEN")).thenReturn(true);
    when(rolePermissionRepository.findPermissionCodesByRoleCodes(List.of("ADMIN")))
        .thenReturn(List.of("rbac:access"));

    PageResponse<StaffUserDto> res = service.listUsers(0, 20, " alice ", false, null);

    assertEquals(1, res.items().size());
    StaffUserDto dto = res.items().get(0);
    assertEquals(u.getId().toString(), dto.userId());
    assertTrue(dto.openResetTicket());
    assertTrue(dto.staff());
    assertEquals(List.of("rbac:access"), dto.permissions());
  }

  @Test
  void listUsers_rejectsInvalidUserId() {
    BusinessException ex =
        assertThrows(
            BusinessException.class,
            () -> service.listUsers(0, 20, null, null, "not-a-uuid"));
    assertEquals("INVALID_USER_ID", ex.getCode());
  }

  @Test
  void getUser_notFound() {
    UUID uid = UUID.randomUUID();
    when(userRepository.findById(uid)).thenReturn(Optional.empty());

    BusinessException ex = assertThrows(BusinessException.class, () -> service.getUser(uid));
    assertEquals("USER_NOT_FOUND", ex.getCode());
  }

  private static UserEntity sampleUser(boolean enabled) {
    UserEntity u = new UserEntity();
    u.setId(UUID.randomUUID());
    u.setUsername("alice");
    u.setEmail("alice@example.com");
    u.setPasswordHash("hash");
    u.setRoles("ADMIN");
    u.setEnabled(enabled);
    u.setMustChangePassword(false);
    u.setCreatedAt(Instant.now());
    return u;
  }
}
