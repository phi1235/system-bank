package com.banksystem.transaction.application.transfer;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.domain.transfer.IdempotencyClaimEntity;
import com.banksystem.transaction.domain.transfer.IdempotencyClaimRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyClaimService {

  private static final Logger log = LoggerFactory.getLogger(IdempotencyClaimService.class);

  private final IdempotencyClaimRepository repository;

  public IdempotencyClaimService(IdempotencyClaimRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public Optional<IdempotencyClaimEntity> claimOrReplay(
      UUID userId, String idempotencyKey, String requestHash, Duration ttl) {
    if (userId == null || idempotencyKey == null || idempotencyKey.isBlank()) {
      return Optional.empty();
    }

    Optional<IdempotencyClaimEntity> existing = repository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
    if (existing.isPresent()) {
      IdempotencyClaimEntity claim = existing.get();
      if (!claim.getRequestHash().equals(requestHash)) {
        log.warn("Idempotency key collision with different hash: user={}, key={}", userId, idempotencyKey);
        throw new BusinessException("IDEMPOTENCY_KEY_REUSED", "Idempotency key has already been used with a different request payload");
      }
      if ("PENDING".equals(claim.getStatus()) || "IN_PROGRESS".equals(claim.getStatus())) {
        throw new BusinessException("IDEMPOTENCY_IN_PROGRESS", "Request with this idempotency key is currently processing. Please retry shortly.");
      }
      return Optional.of(claim);
    }

    Instant expiresAt = Instant.now().plus(ttl != null ? ttl : Duration.ofHours(24));
    IdempotencyClaimEntity newClaim = new IdempotencyClaimEntity(
        userId, idempotencyKey, requestHash, "PENDING", expiresAt);
    repository.saveAndFlush(newClaim);
    return Optional.empty();
  }

  @Transactional
  public void complete(UUID userId, String idempotencyKey, int statusCode, String responsePayload) {
    if (userId == null || idempotencyKey == null || idempotencyKey.isBlank()) {
      return;
    }
    repository.findByUserIdAndIdempotencyKey(userId, idempotencyKey).ifPresent(claim -> {
      claim.setStatus("COMPLETED");
      claim.setResponseStatusCode(statusCode);
      claim.setResponsePayload(responsePayload);
      repository.save(claim);
    });
  }

  @Transactional
  public void fail(UUID userId, String idempotencyKey, String failureReason) {
    if (userId == null || idempotencyKey == null || idempotencyKey.isBlank()) {
      return;
    }
    repository.findByUserIdAndIdempotencyKey(userId, idempotencyKey).ifPresent(claim -> {
      claim.setStatus("FAILED");
      claim.setResponsePayload(failureReason);
      repository.save(claim);
    });
  }

  @Transactional
  public int cleanupExpiredClaims() {
    return repository.deleteExpiredClaims(Instant.now());
  }
}
