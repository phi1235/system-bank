package com.banksystem.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Basic OWASP-ish security headers on every gateway response. */
@Component
public class SecurityHeadersFilter implements GlobalFilter, Ordered {

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    ServerHttpResponse response = exchange.getResponse();
    response.beforeCommit(() -> {
      HttpHeaders h = response.getHeaders();
      h.set("X-Content-Type-Options", "nosniff");
      h.set("X-Frame-Options", "DENY");
      h.set("Content-Security-Policy",
          "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'");
      h.set("Referrer-Policy", "no-referrer");
      h.set("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()");
      h.set("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
      h.set("X-XSS-Protection", "0");
      h.set("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
      h.set("Pragma", "no-cache");
      h.set("Expires", "0");
      return Mono.empty();
    });
    return chain.filter(exchange);
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }
}
