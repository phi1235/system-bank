package com.banksystem.account.domain.ledger;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountInboxRepository extends JpaRepository<AccountInboxEntity, UUID> {

  @Modifying
  @Query(value = "INSERT INTO account_inbox_events (event_id, event_type, processed_at) VALUES (:eventId, :eventType, :processedAt) ON CONFLICT (event_id) DO NOTHING", nativeQuery = true)
  int insertIfNotExistsNative(
      @Param("eventId") UUID eventId,
      @Param("eventType") String eventType,
      @Param("processedAt") Instant processedAt);
}
