package com.banksystem.transaction.domain.forensics;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RemediationOutboxRepository extends JpaRepository<RemediationOutboxEntity, UUID> {

  @Query(value = "SELECT * FROM remediation_outbox_events WHERE (status = 'PENDING' AND next_attempt_at <= :now) OR (status = 'SENDING' AND lease_until < :now) ORDER BY created_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
  List<RemediationOutboxEntity> claimPendingEventsNative(@Param("now") Instant now, @Param("limit") int limit);
}
