package com.banksystem.gateway.filter;

import com.banksystem.common.security.SecurityHeaders;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Removes all caller-controlled headers that are reserved for trusted infrastructure. */
@Component
public class TrustedHeadersSanitizerFilter implements GlobalFilter, Ordered {

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    ServerHttpRequest request = exchange.getRequest().mutate()
        .headers(headers -> {
          headers.remove(SecurityHeaders.USER_ID);
          headers.remove(SecurityHeaders.USER_ROLES);
          headers.remove(SecurityHeaders.USER_PERMISSIONS);
          headers.remove(SecurityHeaders.USER_REALM);
          headers.remove(SecurityHeaders.INTERNAL_API_KEY);
          headers.remove(SecurityHeaders.GATEWAY_TIMESTAMP);
          headers.remove(SecurityHeaders.GATEWAY_SIGNATURE);
          headers.remove(SecurityHeaders.B2B_CLIENT_ID);
          headers.remove(SecurityHeaders.B2B_SCOPES);
          headers.remove(SecurityHeaders.B2B_ORG_TAX);
        })
        .build();
    return chain.filter(exchange.mutate().request(request).build());
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 2;
  }
}
