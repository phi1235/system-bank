package com.banksystem.auth.infrastructure.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.banksystem.auth.domain.UserEntity;
import com.banksystem.auth.infrastructure.jwt.JwtService.TokenPair;
import com.banksystem.common.security.SecurityHeaders;
import io.jsonwebtoken.Claims;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  private JwtService jwtService;

  @BeforeEach
  void setUp() {
    JwtProperties props = new JwtProperties();
    props.setSecret("bank-system-dev-jwt-secret-key-min-32-chars!!");
    props.setAccessTtlSeconds(900);
    props.setRefreshTtlSeconds(3600);
    props.setMfaTtlSeconds(300);
    jwtService = new JwtService(props);
  }

  @Test
  void issueAndParseAccessToken() {
    UserEntity user = sampleUser();
    TokenPair pair = jwtService.issueSessionTokens(user);
    Claims claims = jwtService.parse(pair.accessToken());
    assertEquals(user.getId().toString(), claims.getSubject());
    assertTrue(jwtService.isType(claims, JwtService.TYPE_ACCESS));
    assertEquals("nguyenphi", claims.get("username"));
  }

  @Test
  void mfaTokenType() {
    UserEntity user = sampleUser();
    String token = jwtService.issueMfaToken(user);
    Claims claims = jwtService.parse(token);
    assertTrue(jwtService.isType(claims, JwtService.TYPE_MFA));
    assertEquals("INTERNET_BANKING", claims.get(SecurityHeaders.JWT_CLAIM_REALM));
  }

  private UserEntity sampleUser() {
    UserEntity u = new UserEntity();
    u.setId(UUID.randomUUID());
    u.setUsername("nguyenphi");
    u.setEmail("phi@example.com");
    u.setRoles("CUSTOMER");
    return u;
  }
}
