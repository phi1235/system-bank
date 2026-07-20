package com.banksystem.transaction.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

  /**
   * Claim a batch of ready outbox rows for this poller instance.
   * SKIP LOCKED avoids multi-instance double-processing of the same row.
   */
  @Query(value = """
      SELECT * FROM outbox_events
      WHERE published_at IS NULL
        AND status = 'PENDING'
        AND next_attempt_at <= :now
      ORDER BY created_at ASC
      LIMIT :limit
      FOR UPDATE SKIP LOCKED
      """, nativeQuery = true)
  List<OutboxEventEntity> claimReady(@Param("now") Instant now, @Param("limit") int limit);

  /** Backward-compatible alias used by older call sites/tests if any. */
  default List<OutboxEventEntity> findUnpublished(int limit) {
    return claimReady(Instant.now(), limit);
  }
}
