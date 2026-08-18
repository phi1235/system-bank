package com.banksystem.transaction.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "bank.sepay")
public class SepayProperties {

  private String apiKey = "";
  private String accountNumber = "0123456789";
  private String bankName = "MBBank";
  private String accountName = "NGUYEN CHAU PHI";
  private String qrTemplate = "compact2";
  private int orderExpiryMinutes = 15;
  private String webhookSecret = "";

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  public String getAccountNumber() {
    return accountNumber;
  }

  public void setAccountNumber(String accountNumber) {
    this.accountNumber = accountNumber;
  }

  public String getBankName() {
    return bankName;
  }

  public void setBankName(String bankName) {
    this.bankName = bankName;
  }

  public String getAccountName() {
    return accountName;
  }

  public void setAccountName(String accountName) {
    this.accountName = accountName;
  }

  public String getQrTemplate() {
    return qrTemplate;
  }

  public void setQrTemplate(String qrTemplate) {
    this.qrTemplate = qrTemplate;
  }

  public int getOrderExpiryMinutes() {
    return orderExpiryMinutes;
  }

  public void setOrderExpiryMinutes(int orderExpiryMinutes) {
    this.orderExpiryMinutes = orderExpiryMinutes;
  }

  public String getWebhookSecret() {
    return webhookSecret;
  }

  public void setWebhookSecret(String webhookSecret) {
    this.webhookSecret = webhookSecret;
  }
}
