package com.banksystem.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ServletSecurityHeadersFilterTest {

  @Test
  void addsCompleteSecurityHeaderBaseline() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    new ServletSecurityHeadersFilter().doFilter(
        new MockHttpServletRequest("GET", "/api/v1/test"),
        response,
        new MockFilterChain());

    assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
    assertEquals("DENY", response.getHeader("X-Frame-Options"));
    assertEquals("no-referrer", response.getHeader("Referrer-Policy"));
    assertEquals("max-age=31536000; includeSubDomains",
        response.getHeader("Strict-Transport-Security"));
    assertEquals("camera=(), microphone=(), geolocation=(), payment=()",
        response.getHeader("Permissions-Policy"));
    assertEquals("default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'",
        response.getHeader("Content-Security-Policy"));
  }
}
