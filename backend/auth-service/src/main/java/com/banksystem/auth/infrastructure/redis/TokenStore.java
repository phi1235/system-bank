package com.banksystem.auth.infrastructure.redis;
import static com.banksystem.auth.api.dto.AuthDtos.*;
import static com.banksystem.auth.api.dto.PasswordResetDtos.*;
import static com.banksystem.auth.api.dto.RbacDtos.*;
import com.banksystem.auth.application.auth.*;
import com.banksystem.auth.application.rbac.*;
import com.banksystem.auth.domain.auth.*;
import com.banksystem.auth.domain.rbac.*;
import com.banksystem.auth.api.dto.*;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class TokenStore {

  private static final String REFRESH = "bank:auth:refresh:";
  private static final String BLACKLIST = "bank:auth:bl:";
  private static final String LOGIN_FAIL = "bank:auth:fail:";
  private static final String MFA_SETUP = "bank:mfa:setup:";

  private final StringRedisTemplate redis;

  public TokenStore(StringRedisTemplate redis) {
    this.redis = redis;
  }

  public void storeRefresh(String jti, UUID userId, long ttlSeconds) {
    redis.opsForValue().set(REFRESH + jti, userId.toString(), Duration.ofSeconds(ttlSeconds));
  }

  public Optional<UUID> getRefreshUser(String jti) {
    String val = redis.opsForValue().get(REFRESH + jti);
    return val == null ? Optional.empty() : Optional.of(UUID.fromString(val));
  }

  public void deleteRefresh(String jti) {
    redis.delete(REFRESH + jti);
  }

  public void blacklist(String jti, long ttlSeconds) {
    redis.opsForValue().set(BLACKLIST + jti, "1", Duration.ofSeconds(Math.max(ttlSeconds, 1)));
  }

  public boolean isBlacklisted(String jti) {
    return Boolean.TRUE.equals(redis.hasKey(BLACKLIST + jti));
  }

  public long incrementLoginFail(String ip, long lockMinutes) {
    String key = LOGIN_FAIL + ip;
    Long count = redis.opsForValue().increment(key);
    if (count != null && count == 1L) {
      redis.expire(key, Duration.ofMinutes(lockMinutes));
    }
    return count == null ? 0 : count;
  }

  public void clearLoginFail(String ip) {
    redis.delete(LOGIN_FAIL + ip);
  }

  public long getLoginFail(String ip) {
    String v = redis.opsForValue().get(LOGIN_FAIL + ip);
    if (v == null) {
      return 0;
    }
    try {
      return Long.parseLong(v);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  public void storeMfaSetupSecret(UUID userId, String secret, long ttlMinutes) {
    redis.opsForValue().set(MFA_SETUP + userId, secret, Duration.ofMinutes(ttlMinutes));
  }

  public Optional<String> getMfaSetupSecret(UUID userId) {
    return Optional.ofNullable(redis.opsForValue().get(MFA_SETUP + userId));
  }

  public void clearMfaSetupSecret(UUID userId) {
    redis.delete(MFA_SETUP + userId);
  }
}
