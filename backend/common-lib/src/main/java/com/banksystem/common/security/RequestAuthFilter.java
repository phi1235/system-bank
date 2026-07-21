package com.banksystem.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Parses trusted gateway identity headers into a request-scoped {@link GatewayUser}.
 * Services already scan {@code com.banksystem}, so this filter is auto-registered.
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RequestAuthFilter extends OncePerRequestFilter {

  public static final String ATTR = "gatewayUser";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String userId = request.getHeader(SecurityHeaders.USER_ID);
    if (userId != null && !userId.isBlank()) {
      List<String> roles = splitCsv(request.getHeader(SecurityHeaders.USER_ROLES));
      List<String> permissions = splitCsv(request.getHeader(SecurityHeaders.USER_PERMISSIONS));
      try {
        GatewayUser user = new GatewayUser(UUID.fromString(userId), roles, permissions);
        // Use servlet request attributes — RequestContextHolder is not always bound yet in filters.
        request.setAttribute(ATTR, user);
      } catch (IllegalArgumentException ignored) {
        // invalid user id header — leave unauthenticated
      }
    }
    filterChain.doFilter(request, response);
  }

  private static List<String> splitCsv(String header) {
    if (header == null || header.isBlank()) {
      return List.of();
    }
    return Arrays.stream(header.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
  }
}
