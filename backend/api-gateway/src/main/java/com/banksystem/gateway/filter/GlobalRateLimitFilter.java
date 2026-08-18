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
 * Coarse global rate limit: default 100 req / minute / IP (excludes actuator).
 */
@Component
public class GlobalRateLimitFilter implements GlobalFilter, Ordered {

  private final ReactiveStringRedisTemplate redis;
  private final int limit;
  private final Duration window;
  private final boolean enabled;

  public GlobalRateLimitFilter(
      ReactiveStringRedisTemplate redis,
      @Value("${bank.rate-limit.global.enabled}") boolean enabled,
      @Value("${bank.rate-limit.global.limit}") int limit,
      @Value("${bank.rate-limit.global.window-seconds}") int windowSeconds) {
    this.redis = redis;
    this.enabled = enabled;
    this.limit = limit;
    this.window = Duration.ofSeconds(windowSeconds);
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    if (!enabled) {
      return chain.filter(exchange);
    }
    String path = exchange.getRequest().getURI().getPath();
    if (path.startsWith("/actuator")) {
      return chain.filter(exchange);
    }

    String ip = clientIp(exchange.getRequest());
    String key = "rl:global:" + ip;

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
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            String body = """
                {"success":false,"error":{"code":"RATE_LIMITED","message":"Too many requests"}}
                """.trim();
            DataBuffer buf = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
            return exchange.getResponse().writeWith(Mono.just(buf));
          }
          return chain.filter(exchange);
        })
        .onErrorResume(ex -> chain.filter(exchange));
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
    return -210;
  }
}
