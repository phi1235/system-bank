package com.banksystem.auth.infrastructure.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bank.jwt")
public class JwtProperties {
  private String secret;
  private long accessTtlSeconds = 900;
  private long refreshTtlSeconds = 604800;
  private long mfaTtlSeconds = 300;

  public String getSecret() {
    return secret;
  }

  public void setSecret(String secret) {
    this.secret = secret;
  }

  public long getAccessTtlSeconds() {
    return accessTtlSeconds;
  }

  public void setAccessTtlSeconds(long accessTtlSeconds) {
    this.accessTtlSeconds = accessTtlSeconds;
  }

  public long getRefreshTtlSeconds() {
    return refreshTtlSeconds;
  }

  public void setRefreshTtlSeconds(long refreshTtlSeconds) {
    this.refreshTtlSeconds = refreshTtlSeconds;
  }

  public long getMfaTtlSeconds() {
    return mfaTtlSeconds;
  }

  public void setMfaTtlSeconds(long mfaTtlSeconds) {
    this.mfaTtlSeconds = mfaTtlSeconds;
  }
}
