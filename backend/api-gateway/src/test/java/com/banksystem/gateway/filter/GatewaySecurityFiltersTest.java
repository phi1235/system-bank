package com.banksystem.gateway.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.banksystem.common.security.GatewayRequestSigner;
import com.banksystem.common.security.SecurityHeaders;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class GatewaySecurityFiltersTest {

  private static final String SECRET = "test-gateway-signing-secret-at-least-32-bytes";

  @Test
  void securityHeadersCoverShortCircuitedErrors() {
    MockServerWebExchange exchange = MockServerWebExchange.from(
        MockServerHttpRequest.get("/api/v1/accounts").build());
    GatewayFilterChain shortCircuit = current -> {
      current.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
      return current.getResponse().setComplete();
    };

    new SecurityHeadersFilter().filter(exchange, shortCircuit).block();

    assertEquals("nosniff", exchange.getResponse().getHeaders()
        .getFirst("X-Content-Type-Options"));
    assertEquals("max-age=31536000; includeSubDomains", exchange.getResponse().getHeaders()
        .getFirst("Strict-Transport-Security"));
    assertNotNull(exchange.getResponse().getHeaders().getFirst("Content-Security-Policy"));
  }

  @Test
  void stripsCallerControlledTrustedHeaders() {
    MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
        .get("/api/v1/auth/login")
        .header(SecurityHeaders.USER_ID, "attacker")
        .header(SecurityHeaders.USER_PERMISSIONS, "accounts:freeze:execute")
        .header(SecurityHeaders.GATEWAY_SIGNATURE, "forged")
        .header(SecurityHeaders.INTERNAL_API_KEY, "stolen")
        .build());
    AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

    new TrustedHeadersSanitizerFilter().filter(exchange, current -> {
      forwarded.set(current);
      return Mono.empty();
    }).block();

    var headers = forwarded.get().getRequest().getHeaders();
    assertFalse(headers.containsKey(SecurityHeaders.USER_ID));
    assertFalse(headers.containsKey(SecurityHeaders.USER_PERMISSIONS));
    assertFalse(headers.containsKey(SecurityHeaders.GATEWAY_SIGNATURE));
    assertFalse(headers.containsKey(SecurityHeaders.INTERNAL_API_KEY));
  }

  @Test
  void signsFinalAuthenticatedIdentityAndTarget() {
    long timestamp = Instant.parse("2026-07-14T00:00:00Z").toEpochMilli();
    MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
        .get("/api/v1/accounts?page=0")
        .header(SecurityHeaders.USER_ID, "user-1")
        .header(SecurityHeaders.USER_ROLES, "CUSTOMER")
        .header(SecurityHeaders.USER_PERMISSIONS, "accounts:view")
        .header(SecurityHeaders.USER_REALM, "INTERNET_BANKING")
        .build());
    AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
    GatewaySignatureFilter filter = new GatewaySignatureFilter(
        SECRET, Clock.fixed(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC));

    filter.filter(exchange, current -> {
      forwarded.set(current);
      return Mono.empty();
    }).block();

    var request = forwarded.get().getRequest();
    String signature = request.getHeaders().getFirst(SecurityHeaders.GATEWAY_SIGNATURE);
    assertTrue(GatewayRequestSigner.verify(
        signature,
        SECRET,
        "GET",
        "/api/v1/accounts",
        "page=0",
        "user-1",
        "CUSTOMER",
        "accounts:view",
        "INTERNET_BANKING",
        timestamp));
  }
}
