package com.banksystem.gateway.filter;

import com.banksystem.common.security.SecurityHeaders;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.reactive.CorsUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

  private final SecretKey secretKey;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ReactiveStringRedisTemplate redis;

  private static final List<String> PUBLIC_PATHS = List.of(
      "/api/v1/auth/register",
      "/api/v1/auth/login",
      "/api/v1/auth/refresh",
      "/api/v1/auth/mfa/verify",
      // Guest password-reset ticket create (from uat management-password-reset)
      "/api/v1/auth/password-reset/tickets",
      "/api/v1/callbacks/napas/payments",
      // Dev-only sandbox — no auth, local env only
      "/api/v1/dev/notifications/sandbox"
      // /actuator is intentionally NOT public on the gateway app port;
      // management endpoints live on MANAGEMENT_SERVER_PORT.
  );

  public JwtAuthFilter(
      @Value("${bank.jwt.secret}") String secret,
      ReactiveStringRedisTemplate redis) {
    byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
    if (keyBytes.length < 32) {
      throw new IllegalArgumentException("JWT secret must contain at least 32 bytes");
    }
    this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    this.redis = redis;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    if (CorsUtils.isPreFlightRequest(exchange.getRequest())
        || HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())) {
      return chain.filter(exchange);
    }
    String path = exchange.getRequest().getURI().getPath();
    if (isPublic(path)) {
      return chain.filter(exchange);
    }

    String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    if (auth == null || !auth.startsWith("Bearer ")) {
      return unauthorized(exchange, "Missing or invalid Authorization header");
    }

    final Claims claims;
    try {
      String token = auth.substring(7);
      claims = Jwts.parser()
          .verifyWith(secretKey)
          .build()
          .parseSignedClaims(token)
          .getPayload();
    } catch (Exception ex) {
      return unauthorized(exchange, "Invalid or expired token");
    }

    String typ = claims.get(SecurityHeaders.JWT_CLAIM_TYPE, String.class);
    if (!"access".equals(typ)) {
      return unauthorized(exchange, "Access token required");
    }

    String jti = claims.getId();
    Mono<Boolean> blacklisted = jti == null
        ? Mono.just(false)
        : redis.hasKey("bank:auth:bl:" + jti).defaultIfEmpty(false);

    return blacklisted.flatMap(bl -> {
      if (Boolean.TRUE.equals(bl)) {
        return unauthorized(exchange, "Token has been revoked");
      }
      String userId = claims.getSubject();
      String roles = normalizeListClaim(claims.get(SecurityHeaders.JWT_CLAIM_ROLES));
      String permissions = normalizeListClaim(claims.get(SecurityHeaders.JWT_CLAIM_PERMISSIONS));
      String realm = claims.get(SecurityHeaders.JWT_CLAIM_REALM, String.class);
      ServerHttpRequest request = exchange.getRequest().mutate()
          .headers(headers -> {
            headers.set(SecurityHeaders.USER_ID, userId == null ? "" : userId);
            headers.set(SecurityHeaders.USER_ROLES, roles);
            headers.set(SecurityHeaders.USER_PERMISSIONS, permissions);
            headers.set(SecurityHeaders.USER_REALM, realm == null ? "" : realm);
          })
          .build();
      return chain.filter(exchange.mutate().request(request).build());
    });
  }

  private boolean isPublic(String path) {
    return PUBLIC_PATHS.contains(path) || path.equals("/");
  }

  private String normalizeListClaim(Object claim) {
    if (claim == null) {
      return "";
    }
    if (claim instanceof List<?> list) {
      return list.stream().map(Object::toString).collect(Collectors.joining(","));
    }
    return claim.toString();
  }

  private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
    String correlationId = exchange.getRequest().getHeaders().getFirst(SecurityHeaders.CORRELATION_ID);
    byte[] bytes;
    try {
      bytes = objectMapper.writeValueAsBytes(Map.of(
          "success", false,
          "error", Map.of("code", "UNAUTHORIZED", "message", message),
          "meta", Map.of("correlationId", correlationId == null ? "" : correlationId)
      ));
    } catch (JsonProcessingException e) {
      bytes = "{\"success\":false}".getBytes(StandardCharsets.UTF_8);
    }
    DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
    return exchange.getResponse().writeWith(Mono.just(buffer));
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 4;
  }
}
