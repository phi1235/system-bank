package com.banksystem.transaction.infrastructure.redis;

import com.banksystem.transaction.api.dto.BeneficiaryDtos.BeneficiaryResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Read-through cache for a user's active beneficiary address book.
 * Invalidate on create / rename / soft-delete so list stays coherent.
 */
@Component
public class BeneficiaryListCache {

  private static final Logger log = LoggerFactory.getLogger(BeneficiaryListCache.class);
  private static final String KEY_PREFIX = "bank:tx:beneficiaries:";
  private static final TypeReference<List<BeneficiaryResponse>> LIST_TYPE = new TypeReference<>() {};

  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;
  private final Duration ttl;

  public BeneficiaryListCache(
      StringRedisTemplate redis,
      ObjectMapper objectMapper,
      @Value("${bank.beneficiary.cache-ttl-seconds}") long ttlSeconds) {
    this.redis = redis;
    this.objectMapper = objectMapper;
    this.ttl = Duration.ofSeconds(Math.max(ttlSeconds, 1));
  }

  public Optional<List<BeneficiaryResponse>> get(UUID userId) {
    try {
      String json = redis.opsForValue().get(key(userId));
      if (json == null || json.isBlank()) {
        return Optional.empty();
      }
      return Optional.of(objectMapper.readValue(json, LIST_TYPE));
    } catch (Exception ex) {
      log.warn("Beneficiary cache read failed for {}: {}", userId, ex.toString());
      return Optional.empty();
    }
  }

  public void put(UUID userId, List<BeneficiaryResponse> items) {
    try {
      String json = objectMapper.writeValueAsString(items);
      redis.opsForValue().set(key(userId), json, ttl);
    } catch (JsonProcessingException ex) {
      log.warn("Beneficiary cache write failed for {}: {}", userId, ex.toString());
    } catch (Exception ex) {
      log.warn("Beneficiary cache write failed for {}: {}", userId, ex.toString());
    }
  }

  public void evict(UUID userId) {
    try {
      redis.delete(key(userId));
    } catch (Exception ex) {
      log.warn("Beneficiary cache evict failed for {}: {}", userId, ex.toString());
    }
  }

  private static String key(UUID userId) {
    return KEY_PREFIX + userId;
  }
}
