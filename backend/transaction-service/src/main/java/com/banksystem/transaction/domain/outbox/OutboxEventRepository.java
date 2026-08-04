package com.banksystem.transaction.domain.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
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

  Page<OutboxEventEntity> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

  /**
   * Admin outbox search. Callers pass concrete from/to bounds and boolean flags so Postgres
   * never sees untyped NULL UUID binds.
   */
  @Query("""
      SELECT e FROM OutboxEventEntity e
      WHERE e.status = :status
        AND (:hasEventType = false OR LOWER(e.eventType) = LOWER(:eventType))
        AND (:hasEventId = false OR e.id = :eventId)
        AND (:hasAggregateId = false OR e.aggregateId = :aggregateId)
        AND (:hasQ = false
          OR LOWER(e.eventType) LIKE LOWER(CONCAT('%', :q, '%'))
          OR LOWER(e.aggregateType) LIKE LOWER(CONCAT('%', :q, '%'))
          OR LOWER(COALESCE(e.lastError, '')) LIKE LOWER(CONCAT('%', :q, '%')))
        AND e.createdAt >= :fromTs
        AND e.createdAt <= :toTs
      ORDER BY e.createdAt DESC
      """)
  Page<OutboxEventEntity> searchAdmin(
      @Param("status") String status,
      @Param("hasEventType") boolean hasEventType,
      @Param("eventType") String eventType,
      @Param("hasEventId") boolean hasEventId,
      @Param("eventId") UUID eventId,
      @Param("hasAggregateId") boolean hasAggregateId,
      @Param("aggregateId") UUID aggregateId,
      @Param("hasQ") boolean hasQ,
      @Param("q") String q,
      @Param("fromTs") Instant fromTs,
      @Param("toTs") Instant toTs,
      Pageable pageable);

  @Query("""
      SELECT e FROM OutboxEventEntity e
      WHERE e.status = :status
        AND (:hasEventType = false OR LOWER(e.eventType) = LOWER(:eventType))
        AND (:hasEventId = false OR e.id = :eventId)
        AND (:hasAggregateId = false OR e.aggregateId = :aggregateId)
        AND (:hasQ = false
          OR LOWER(e.eventType) LIKE LOWER(CONCAT('%', :q, '%'))
          OR LOWER(e.aggregateType) LIKE LOWER(CONCAT('%', :q, '%'))
          OR LOWER(COALESCE(e.lastError, '')) LIKE LOWER(CONCAT('%', :q, '%')))
        AND e.createdAt >= :fromTs
        AND e.createdAt <= :toTs
      ORDER BY e.createdAt DESC
      """)
  Slice<OutboxEventEntity> searchAdminSlice(
      @Param("status") String status,
      @Param("hasEventType") boolean hasEventType,
      @Param("eventType") String eventType,
      @Param("hasEventId") boolean hasEventId,
      @Param("eventId") UUID eventId,
      @Param("hasAggregateId") boolean hasAggregateId,
      @Param("aggregateId") UUID aggregateId,
      @Param("hasQ") boolean hasQ,
      @Param("q") String q,
      @Param("fromTs") Instant fromTs,
      @Param("toTs") Instant toTs,
      Pageable pageable);

  long countByStatus(String status);
}
