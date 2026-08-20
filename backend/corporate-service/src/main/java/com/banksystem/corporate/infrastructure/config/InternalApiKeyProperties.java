package com.banksystem.corporate.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "bank.internal")
public class InternalApiKeyProperties {

  private String apiKey;
  private String accountApiKey;
  private String userApiKey;
  private String transactionApiKey;
  private String notificationApiKey;

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  public String getAccountApiKey() {
    return accountApiKey;
  }

  public void setAccountApiKey(String accountApiKey) {
    this.accountApiKey = accountApiKey;
  }

  public String getUserApiKey() {
    return userApiKey;
  }

  public void setUserApiKey(String userApiKey) {
    this.userApiKey = userApiKey;
  }

  public String getTransactionApiKey() {
    return transactionApiKey;
  }

  public void setTransactionApiKey(String transactionApiKey) {
    this.transactionApiKey = transactionApiKey;
  }

  public String getNotificationApiKey() {
    return notificationApiKey;
  }

  public void setNotificationApiKey(String notificationApiKey) {
    this.notificationApiKey = notificationApiKey;
  }

  public String getEffectiveAccountApiKey() {
    return accountApiKey != null ? accountApiKey : apiKey;
  }

  public String getEffectiveUserApiKey() {
    return userApiKey != null ? userApiKey : apiKey;
  }

  public String getEffectiveTransactionApiKey() {
    return transactionApiKey != null ? transactionApiKey : apiKey;
  }

  public String getEffectiveNotificationApiKey() {
    return notificationApiKey != null ? notificationApiKey : apiKey;
  }
}
