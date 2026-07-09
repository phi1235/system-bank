package com.banksystem.gateway.filter;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Rate-limit POST /api/v1/auth/login: default 5 requests / minute / IP (Redis).
 */
@Component
public class LoginRateLimitFilter implements GlobalFilter, Ordered {

  private final ReactiveStringRedisTemplate redis;
  private final int limit;
  private final Duration window;

  public LoginRateLimitFilter(
      ReactiveStringRedisTemplate redis,
      @Value("${bank.rate-limit.login.limit:5}") int limit,
      @Value("${bank.rate-limit.login.window-seconds:60}") int windowSeconds) {
    this.redis = redis;
    this.limit = limit;
    this.window = Duration.ofSeconds(windowSeconds);
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    ServerHttpRequest req = exchange.getRequest();
    if (!"POST".equalsIgnoreCase(req.getMethod().name())) {
      return chain.filter(exchange);
    }
    String path = req.getURI().getPath();
    if (!path.endsWith("/auth/login") && !path.equals("/api/v1/auth/login")) {
      return chain.filter(exchange);
    }

    String ip = clientIp(req);
    String key = "rl:login:" + ip;

    return redis.opsForValue()
        .increment(key)
        .flatMap(count -> {
          Mono<Boolean> expire = count != null && count == 1
              ? redis.expire(key, window)
              : Mono.just(true);
          return expire.thenReturn(count == null ? 1L : count);
        })
        .flatMap(count -> {
          if (count > limit) {
            return tooMany(exchange, ip);
          }
          return chain.filter(exchange);
        })
        .onErrorResume(ex -> {
          // Redis down: fail-open for availability (log via status path)
          return chain.filter(exchange);
        });
  }

  private Mono<Void> tooMany(ServerWebExchange exchange, String ip) {
    exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
    exchange.getResponse().getHeaders().add("Retry-After", String.valueOf(window.toSeconds()));
    String body = """
        {"success":false,"error":{"code":"RATE_LIMITED","message":"Too many login attempts. Try again later.","details":["ip=%s","limit=%d/min"]}}
        """.formatted(ip, limit).trim();
    DataBuffer buf = exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
    return exchange.getResponse().writeWith(Mono.just(buf));
  }

  private String clientIp(ServerHttpRequest req) {
    String xff = req.getHeaders().getFirst("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
      return xff.split(",")[0].trim();
    }
    if (req.getRemoteAddress() != null && req.getRemoteAddress().getAddress() != null) {
      return req.getRemoteAddress().getAddress().getHostAddress();
    }
    return "unknown";
  }

  @Override
  public int getOrder() {
    return -200; // before JWT
  }
}
