package com.banksystem.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.filter.OncePerRequestFilter;

/** Rejects public API calls that were not signed by the API Gateway. */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class GatewayTrustFilter extends OncePerRequestFilter {

  private final String secret;
  private final long maxClockSkewMillis;
  private final Clock clock;

  @Autowired
  public GatewayTrustFilter(
      @Value("${bank.gateway.signing-secret}") String secret,
      @Value("${bank.gateway.max-clock-skew-seconds}") long maxClockSkewSeconds) {
    this(secret, maxClockSkewSeconds, Clock.systemUTC());
  }

  GatewayTrustFilter(String secret, long maxClockSkewSeconds, Clock clock) {
    GatewayRequestSigner.sign(secret, "GET", "/startup-check", null,
        null, null, null, null, 0);
    this.secret = secret;
    this.maxClockSkewMillis = Math.multiplyExact(maxClockSkewSeconds, 1_000L);
    this.clock = clock;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return "OPTIONS".equalsIgnoreCase(request.getMethod()) || !request.getRequestURI().startsWith("/api/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (!isTrusted(request)) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.getWriter().write(
          "{\"success\":false,\"error\":{\"code\":\"UNTRUSTED_GATEWAY\","
              + "\"message\":\"Request must pass through the API Gateway\"}}");
      return;
    }
    filterChain.doFilter(request, response);
  }

  private boolean isTrusted(HttpServletRequest request) {
    String timestampHeader = request.getHeader(SecurityHeaders.GATEWAY_TIMESTAMP);
    long timestamp;
    try {
      timestamp = Long.parseLong(timestampHeader);
    } catch (RuntimeException ex) {
      return false;
    }
    long now = clock.millis();
    if (timestamp < now - maxClockSkewMillis
        || timestamp > now + maxClockSkewMillis) {
      return false;
    }
    return GatewayRequestSigner.verify(
        request.getHeader(SecurityHeaders.GATEWAY_SIGNATURE),
        secret,
        request.getMethod(),
        request.getRequestURI(),
        request.getQueryString(),
        request.getHeader(SecurityHeaders.USER_ID),
        request.getHeader(SecurityHeaders.USER_ROLES),
        request.getHeader(SecurityHeaders.USER_PERMISSIONS),
        request.getHeader(SecurityHeaders.USER_REALM),
        timestamp);
  }
}
