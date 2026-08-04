package com.banksystem.auth.infrastructure.redis;
import static com.banksystem.auth.api.dto.AuthDtos.*;
import static com.banksystem.auth.api.dto.PasswordResetDtos.*;
import static com.banksystem.auth.api.dto.RbacDtos.*;
import com.banksystem.auth.application.auth.*;
import com.banksystem.auth.application.rbac.*;
import com.banksystem.auth.domain.auth.*;
import com.banksystem.auth.domain.rbac.*;
import com.banksystem.auth.api.dto.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Tracks active refresh sessions per user so clients can list / revoke devices.
 * Key layout:
 *   bank:auth:session:{jti} -> JSON metadata (TTL = refresh TTL)
 *   bank:auth:user-sessions:{userId} -> SET of jti (TTL refreshed on each login)
 */
@Component
public class SessionStore {

  private static final Logger log = LoggerFactory.getLogger(SessionStore.class);
  private static final String SESSION = "bank:auth:session:";
  private static final String USER_SESSIONS = "bank:auth:user-sessions:";

  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;

  public SessionStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
    this.redis = redis;
    this.objectMapper = objectMapper;
  }

  public void save(SessionMeta meta, long ttlSeconds) {
    long ttl = Math.max(ttlSeconds, 1);
    try {
      String json = objectMapper.writeValueAsString(meta);
      redis.opsForValue().set(SESSION + meta.jti(), json, Duration.ofSeconds(ttl));
      String indexKey = USER_SESSIONS + meta.userId();
      redis.opsForSet().add(indexKey, meta.jti());
      redis.expire(indexKey, Duration.ofSeconds(ttl));
    } catch (JsonProcessingException ex) {
      log.warn("Session save serialize failed jti={}: {}", meta.jti(), ex.toString());
    } catch (Exception ex) {
      log.warn("Session save failed jti={}: {}", meta.jti(), ex.toString());
    }
  }

  public Optional<SessionMeta> get(String jti) {
    try {
      String json = redis.opsForValue().get(SESSION + jti);
      if (json == null || json.isBlank()) {
        return Optional.empty();
      }
      return Optional.of(objectMapper.readValue(json, SessionMeta.class));
    } catch (Exception ex) {
      log.warn("Session get failed jti={}: {}", jti, ex.toString());
      return Optional.empty();
    }
  }

  public List<SessionMeta> listByUser(UUID userId) {
    try {
      Set<String> jtis = redis.opsForSet().members(USER_SESSIONS + userId);
      if (jtis == null || jtis.isEmpty()) {
        return List.of();
      }
      List<SessionMeta> result = new ArrayList<>();
      List<String> stale = new ArrayList<>();
      for (String jti : jtis) {
        Optional<SessionMeta> meta = get(jti);
        if (meta.isPresent()) {
          result.add(meta.get());
        } else {
          stale.add(jti);
        }
      }
      if (!stale.isEmpty()) {
        redis.opsForSet().remove(USER_SESSIONS + userId, (Object[]) stale.toArray(String[]::new));
      }
      result.sort((a, b) -> b.createdAt().compareTo(a.createdAt()));
      return result;
    } catch (Exception ex) {
      log.warn("Session list failed userId={}: {}", userId, ex.toString());
      return Collections.emptyList();
    }
  }

  /** Removes session metadata + index entry. Does not touch refresh/blacklist keys. */
  public void delete(UUID userId, String jti) {
    try {
      redis.delete(SESSION + jti);
      redis.opsForSet().remove(USER_SESSIONS + userId, jti);
    } catch (Exception ex) {
      log.warn("Session delete failed jti={}: {}", jti, ex.toString());
    }
  }

  public record SessionMeta(
      String jti,
      UUID userId,
      String ip,
      String userAgent,
      Instant createdAt,
      Instant expiresAt
  ) {}
}
