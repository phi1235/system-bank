package com.banksystem.transaction.domain.forensics;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface ForensicExportJobRepository extends JpaRepository<ForensicExportJobEntity, UUID> {
  List<ForensicExportJobEntity> findTop100ByExpiresAtBeforeAndStatusNot(Instant now, String status);
  @Modifying @Transactional
  @Query("UPDATE ForensicExportJobEntity e SET e.status = 'EXPIRED', e.storageUri = null WHERE e.id = :id")
  int markExpired(UUID id);
}
