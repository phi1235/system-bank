package com.banksystem.common.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GatewayUserTest {

  private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  @Test
  void hasPermission_matchesCaseInsensitive() {
    GatewayUser u = new GatewayUser(ID, List.of("CUSTOMER"), List.of("ib:home:view"));
    assertTrue(u.hasPermission("IB:HOME:VIEW"));
    assertFalse(u.hasPermission("ib:transfer:execute"));
  }

  @Test
  void adminRoleBypassesPermissionList() {
    GatewayUser u = new GatewayUser(ID, List.of("ROLE_ADMIN"), List.of());
    assertTrue(u.hasPermission("anything:goes"));
  }

  @Test
  void starPermissionGrantsAll() {
    GatewayUser u = new GatewayUser(ID, List.of("CUSTOMER"), List.of("*"));
    assertTrue(u.hasPermission("rbac:access"));
  }

  @Test
  void nullPermissionIsFalse() {
    GatewayUser u = new GatewayUser(ID, List.of("CUSTOMER"), List.of("ib:home:view"));
    assertFalse(u.hasPermission(null));
  }
}
