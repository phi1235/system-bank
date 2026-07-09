package com.banksystem.customer.config;

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
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

@Component
public class RequestAuthFilter extends OncePerRequestFilter {

  public static final String ATTR = "gatewayUser";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String userId = request.getHeader(SecurityHeaders.USER_ID);
    String rolesHeader = request.getHeader(SecurityHeaders.USER_ROLES);
    if (userId != null && !userId.isBlank()) {
      List<String> roles = rolesHeader == null || rolesHeader.isBlank()
          ? List.of()
          : Arrays.stream(rolesHeader.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
      try {
        GatewayUser user = new GatewayUser(UUID.fromString(userId), roles);
        RequestContextHolder.currentRequestAttributes()
            .setAttribute(ATTR, user, RequestAttributes.SCOPE_REQUEST);
      } catch (IllegalArgumentException ignored) {
        // invalid uuid
      }
    }
    filterChain.doFilter(request, response);
  }
}
