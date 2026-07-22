package com.banksystem.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

  @Test
  void generatesWhenMissingAndEchoesHeader() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    new CorrelationIdFilter().doFilter(request, response, chain);

    String header = response.getHeader(SecurityHeaders.CORRELATION_ID);
    assertNotNull(header);
    assertTrue(header.length() >= 8);
    assertEquals(header, request.getAttribute(CorrelationIdFilter.REQUEST_ATTR));
    // MDC cleared after filter completes
    assertNull(MDC.get(CorrelationIdFilter.MDC_KEY));
  }

  @Test
  void reusesInboundHeader() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
    request.addHeader(SecurityHeaders.CORRELATION_ID, "client-corr-123");
    MockHttpServletResponse response = new MockHttpServletResponse();

    new CorrelationIdFilter().doFilter(request, response, new MockFilterChain());

    assertEquals("client-corr-123", response.getHeader(SecurityHeaders.CORRELATION_ID));
    assertEquals("client-corr-123", request.getAttribute(CorrelationIdFilter.REQUEST_ATTR));
  }

  @Test
  void mdcAvailableDuringChain() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
    request.addHeader(SecurityHeaders.CORRELATION_ID, "in-chain");
    MockHttpServletResponse response = new MockHttpServletResponse();
    final String[] seen = {null};

    new CorrelationIdFilter().doFilter(request, response, (req, res) -> {
      seen[0] = MDC.get(CorrelationIdFilter.MDC_KEY);
    });

    assertEquals("in-chain", seen[0]);
    assertNull(MDC.get(CorrelationIdFilter.MDC_KEY));
  }
}
