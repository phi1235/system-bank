package com.banksystem.gateway.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
public class GatewayConfig {

  @Bean
  public CorsWebFilter corsWebFilter(
      @Value("${bank.cors.allowed-origins}") String origins) {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowCredentials(true);
    List<String> allowedOrigins = Arrays.stream(origins.split(","))
        .map(String::trim)
        .filter(origin -> !origin.isBlank())
        .toList();
    if (allowedOrigins.isEmpty()) {
      throw new IllegalStateException("bank.cors.allowed-origins must not be empty");
    }
    config.setAllowedOrigins(allowedOrigins);
    config.addAllowedHeader("*");
    config.addAllowedMethod("*");
    config.addExposedHeader("X-Correlation-Id");

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return new CorsWebFilter(source);
  }
}
