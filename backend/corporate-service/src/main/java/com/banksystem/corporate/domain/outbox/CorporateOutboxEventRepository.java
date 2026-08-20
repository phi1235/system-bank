package com.banksystem.corporate.domain.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CorporateOutboxEventRepository extends JpaRepository<CorporateOutboxEventEntity, UUID> {

  @Query(value = """
      SELECT id FROM corporate_outbox_events
      WHERE status IN ('PENDING', 'SENDING')
        AND next_attempt_at <= :now
        AND (lease_until IS NULL OR lease_until <= :now)
      ORDER BY created_at ASC
      FOR UPDATE SKIP LOCKED
      LIMIT :limit
      """, nativeQuery = true)
  List<UUID> claimEvents(
      @Param("now") Instant now,
      @Param("limit") int limit);

  @Modifying
  @Query("""
      UPDATE CorporateOutboxEventEntity e
      SET e.status = 'SENDING',
          e.claimedBy = :claimedBy,
          e.leaseUntil = :leaseUntil
      WHERE e.id IN :ids
      """)
  int markEventsSending(
      @Param("ids") List<UUID> ids,
      @Param("claimedBy") String claimedBy,
      @Param("leaseUntil") Instant leaseUntil);
}
