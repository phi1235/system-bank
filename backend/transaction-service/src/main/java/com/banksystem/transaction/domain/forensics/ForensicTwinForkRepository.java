package com.banksystem.transaction.domain.forensics;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface ForensicTwinForkRepository extends JpaRepository<ForensicTwinForkEntity, UUID> {
  long countByCreatedByAndStatusAndExpiresAtAfter(UUID createdBy, String status, Instant now);
  List<ForensicTwinForkEntity> findTop100ByExpiresAtBeforeAndStatus(Instant now, String status);
  @Modifying @Transactional
  @Query("UPDATE ForensicTwinForkEntity f SET f.status = 'EXPIRED', f.snapshotUri = '' WHERE f.id = :id")
  int markExpired(UUID id);
}
