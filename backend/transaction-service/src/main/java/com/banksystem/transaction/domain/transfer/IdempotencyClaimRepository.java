package com.banksystem.transaction.domain.transfer;

import com.banksystem.transaction.domain.transfer.IdempotencyClaimEntity.IdempotencyKeyId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IdempotencyClaimRepository extends JpaRepository<IdempotencyClaimEntity, IdempotencyKeyId> {

  Optional<IdempotencyClaimEntity> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

  @Modifying
  @Query("DELETE FROM IdempotencyClaimEntity c WHERE c.expiresAt < :now")
  int deleteExpiredClaims(@Param("now") Instant now);
}
