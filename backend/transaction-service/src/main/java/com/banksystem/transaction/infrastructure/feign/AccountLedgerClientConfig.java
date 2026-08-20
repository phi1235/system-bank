package com.banksystem.transaction.infrastructure.feign;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class AccountLedgerClientConfig {

  @Value("${bank.internal.account-api-key}")
  private String accountApiKey;

  @Bean
  public RequestInterceptor accountLedgerRequestInterceptor() {
    return template -> {
      if (accountApiKey != null && !accountApiKey.isBlank()) {
        template.header("X-Internal-Api-Key", accountApiKey);
      }
    };
  }
}
