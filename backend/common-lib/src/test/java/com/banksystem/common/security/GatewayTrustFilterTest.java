package com.banksystem.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class GatewayTrustFilterTest {

  private static final String SECRET = "test-gateway-signing-secret-at-least-32-bytes";
  private static final Instant NOW = Instant.parse("2026-07-14T00:00:00Z");

  @Test
  void acceptsFreshValidSignature() throws Exception {
    GatewayTrustFilter filter = filter();
    MockHttpServletRequest request = signedRequest(NOW.toEpochMilli());
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertNotNull(chain.getRequest());
    assertEquals(200, response.getStatus());
  }

  @Test
  void rejectsMissingSignature() throws Exception {
    GatewayTrustFilter filter = filter();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertEquals(403, response.getStatus());
  }

  @Test
  void rejectsExpiredSignature() throws Exception {
    GatewayTrustFilter filter = filter();
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(signedRequest(NOW.minusSeconds(31).toEpochMilli()), response,
        new MockFilterChain());

    assertEquals(403, response.getStatus());
  }

  @Test
  void ignoresNonApiEndpoints() throws Exception {
    GatewayTrustFilter filter = filter();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, new MockHttpServletResponse(), chain);

    assertNotNull(chain.getRequest());
  }

  private GatewayTrustFilter filter() {
    return new GatewayTrustFilter(SECRET, 30, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private MockHttpServletRequest signedRequest(long timestamp) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts");
    request.setQueryString("page=0");
    request.addHeader(SecurityHeaders.USER_ID, "user-1");
    request.addHeader(SecurityHeaders.USER_ROLES, "CUSTOMER");
    request.addHeader(SecurityHeaders.USER_PERMISSIONS, "accounts:view");
    request.addHeader(SecurityHeaders.USER_REALM, "INTERNET_BANKING");
    request.addHeader(SecurityHeaders.GATEWAY_TIMESTAMP, Long.toString(timestamp));
    request.addHeader(SecurityHeaders.GATEWAY_SIGNATURE, GatewayRequestSigner.sign(
        SECRET, "GET", "/api/v1/accounts", "page=0", "user-1",
        "CUSTOMER", "accounts:view", "INTERNET_BANKING", timestamp));
    return request;
  }
}
