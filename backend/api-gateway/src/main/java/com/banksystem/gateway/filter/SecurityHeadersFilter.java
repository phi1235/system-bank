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
      h.addIfAbsent("X-Content-Type-Options", "nosniff");
      h.addIfAbsent("X-Frame-Options", "DENY");
      h.addIfAbsent("Referrer-Policy", "no-referrer");
      h.addIfAbsent("X-XSS-Protection", "0");
      h.addIfAbsent("Cache-Control", "no-store");
      return Mono.empty();
    });
    return chain.filter(exchange);
  }

  @Override
  public int getOrder() {
    return -50;
  }
}
