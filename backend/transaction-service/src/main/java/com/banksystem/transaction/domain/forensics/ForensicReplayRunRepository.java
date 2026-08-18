package com.banksystem.transaction.domain.forensics;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface ForensicReplayRunRepository extends JpaRepository<ForensicReplayRunEntity, UUID> {
  Optional<ForensicReplayRunEntity> findByRequestedByAndIdempotencyKey(UUID actor, String key);
  List<ForensicReplayRunEntity> findTop100ByExpiresAtBeforeAndStatusNot(Instant now, String status);
  @Modifying @Transactional
  @Query("UPDATE ForensicReplayRunEntity r SET r.status = 'EXPIRED', r.resultUri = null WHERE r.id = :id")
  int markExpired(UUID id);
}
