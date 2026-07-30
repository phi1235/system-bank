package com.banksystem.gateway.config;

import java.util.Objects;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimiterConfig {

  /**
   * Resolves rate limiting key per client Remote IP address (or X-Forwarded-For).
   */
  @Bean
  @Primary
  public KeyResolver userKeyResolver() {
    return exchange -> {
      String ip = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
      if (ip == null || ip.isBlank()) {
        ip = Objects.requireNonNull(exchange.getRequest().getRemoteAddress())
            .getAddress()
            .getHostAddress();
      }
      return Mono.just(ip);
    };
  }
}
