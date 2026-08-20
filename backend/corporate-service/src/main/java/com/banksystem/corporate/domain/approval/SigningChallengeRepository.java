package com.banksystem.corporate.domain.approval;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

public interface SigningChallengeRepository extends JpaRepository<SigningChallengeEntity, UUID> {
  Optional<SigningChallengeEntity> findByNonce(String nonce);

  Optional<SigningChallengeEntity> findByTaskIdAndUserIdAndVerifiedFalse(UUID taskId, UUID userId);

  @Modifying
  @Query("UPDATE SigningChallengeEntity c SET c.attemptCount = c.attemptCount + 1 "
      + "WHERE c.id = :id AND c.verified = false AND c.attemptCount < :maxAttempts")
  int incrementFailedAttempt(@Param("id") UUID id, @Param("maxAttempts") int maxAttempts);

  @Modifying
  @Query("UPDATE SigningChallengeEntity c SET c.verified = true, c.verifiedAt = :now "
      + "WHERE c.id = :id AND c.verified = false AND c.attemptCount < :maxAttempts AND c.expiresAt > :now")
  int redeem(@Param("id") UUID id, @Param("now") Instant now, @Param("maxAttempts") int maxAttempts);
}
