package com.banksystem.gateway.filter;

import com.banksystem.common.security.GatewayRequestSigner;
import com.banksystem.common.security.SecurityHeaders;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;

/** Signs the sanitized request target and authenticated identity for downstream services. */
@Component
public class GatewaySignatureFilter implements GlobalFilter, Ordered {

  private final String secret;
  private final Clock clock;

  @Autowired
  public GatewaySignatureFilter(@Value("${bank.gateway.signing-secret}") String secret) {
    this(secret, Clock.systemUTC());
  }

  GatewaySignatureFilter(String secret, Clock clock) {
    GatewayRequestSigner.sign(secret, "GET", "/startup-check", null,
        null, null, null, null, 0);
    this.secret = secret;
    this.clock = clock;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    if (org.springframework.web.cors.reactive.CorsUtils.isPreFlightRequest(exchange.getRequest())
        || org.springframework.http.HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())) {
      return chain.filter(exchange);
    }
    ServerHttpRequest current = exchange.getRequest();
    HttpHeaders headers = current.getHeaders();
    long timestamp = clock.millis();
    String signature = GatewayRequestSigner.sign(
        secret,
        current.getMethod().name(),
        current.getURI().getRawPath(),
        current.getURI().getRawQuery(),
        headers.getFirst(SecurityHeaders.USER_ID),
        headers.getFirst(SecurityHeaders.USER_ROLES),
        headers.getFirst(SecurityHeaders.USER_PERMISSIONS),
        headers.getFirst(SecurityHeaders.USER_REALM),
        timestamp);
    ServerHttpRequest signed = current.mutate()
        .headers(out -> {
          out.set(SecurityHeaders.GATEWAY_TIMESTAMP, Long.toString(timestamp));
          out.set(SecurityHeaders.GATEWAY_SIGNATURE, signature);
        })
        .build();
    return chain.filter(exchange.mutate().request(signed).build());
  }

  @Override
  public int getOrder() {
    return -100;
  }
}
