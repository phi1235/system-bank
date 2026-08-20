package com.banksystem.transaction.infrastructure.feign;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class AuthBusinessClientConfig {

  @Value("${bank.internal.auth-api-key}")
  private String authApiKey;

  @Bean
  public RequestInterceptor authBusinessRequestInterceptor() {
    return template -> {
      if (authApiKey != null && !authApiKey.isBlank()) {
        template.header("X-Internal-Api-Key", authApiKey);
      }
    };
  }
}
