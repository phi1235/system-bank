package com.banksystem.transaction.config;

import com.banksystem.common.security.SecurityHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
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
        RequestContextHolder.currentRequestAttributes()
            .setAttribute(ATTR, user, RequestAttributes.SCOPE_REQUEST);
      } catch (IllegalArgumentException ignored) {
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
