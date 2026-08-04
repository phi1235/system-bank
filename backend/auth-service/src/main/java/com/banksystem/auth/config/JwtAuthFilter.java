package com.banksystem.auth.config;
import static com.banksystem.auth.api.dto.AuthDtos.*;
import static com.banksystem.auth.api.dto.PasswordResetDtos.*;
import static com.banksystem.auth.api.dto.RbacDtos.*;
import com.banksystem.auth.application.auth.*;
import com.banksystem.auth.application.rbac.*;
import com.banksystem.auth.domain.auth.*;
import com.banksystem.auth.domain.rbac.*;
import com.banksystem.auth.api.dto.*;

import com.banksystem.auth.infrastructure.jwt.JwtService;
import com.banksystem.auth.infrastructure.redis.TokenStore;
import com.banksystem.common.security.SecurityHeaders;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

  private final JwtService jwtService;
  private final TokenStore tokenStore;

  public JwtAuthFilter(JwtService jwtService, TokenStore tokenStore) {
    this.jwtService = jwtService;
    this.tokenStore = tokenStore;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (auth != null && auth.startsWith("Bearer ")) {
      String token = auth.substring(7);
      try {
        Claims claims = jwtService.parse(token);
        if (jwtService.isType(claims, JwtService.TYPE_ACCESS)
            && !tokenStore.isBlacklisted(claims.getId())) {
          UUID userId = UUID.fromString(claims.getSubject());
          String username = claims.get("username", String.class);
          @SuppressWarnings("unchecked")
          List<String> roles = claims.get(SecurityHeaders.JWT_CLAIM_ROLES, List.class);
          if (roles == null) {
            roles = List.of();
          }
          @SuppressWarnings("unchecked")
          List<String> permissions = claims.get(SecurityHeaders.JWT_CLAIM_PERMISSIONS, List.class);
          if (permissions == null) {
            permissions = List.of();
          }
          UserPrincipal principal = new UserPrincipal(userId, username, roles, permissions);
          var authentication = new UsernamePasswordAuthenticationToken(
              principal, null, principal.getAuthorities());
          SecurityContextHolder.getContext().setAuthentication(authentication);
        }
      } catch (Exception ignored) {
        SecurityContextHolder.clearContext();
      }
    }
    filterChain.doFilter(request, response);
  }
}
