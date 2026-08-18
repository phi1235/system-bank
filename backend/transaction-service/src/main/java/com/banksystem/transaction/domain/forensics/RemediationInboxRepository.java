package com.banksystem.transaction.domain.forensics;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RemediationInboxRepository extends JpaRepository<RemediationInboxEntity, UUID> {

  @Modifying
  @Query(value = "INSERT INTO remediation_inbox_events (event_id, event_type, processed_at) VALUES (:eventId, :eventType, :processedAt) ON CONFLICT (event_id) DO NOTHING", nativeQuery = true)
  int insertIfNotExistsNative(
      @Param("eventId") UUID eventId,
      @Param("eventType") String eventType,
      @Param("processedAt") Instant processedAt);
}
