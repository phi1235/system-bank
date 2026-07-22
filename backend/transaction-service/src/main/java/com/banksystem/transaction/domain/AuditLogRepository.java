package com.banksystem.transaction.domain;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {
  Page<AuditLogEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

  /**
   * Admin audit search. Callers must pass concrete from/to bounds (epoch / far-future when
   * UI omits a range). Optional filters use boolean flags so Postgres never sees untyped NULL
   * binds for UUID/string compares.
   */
  @Query("""
      SELECT a FROM AuditLogEntity a
      WHERE (:hasAction = false OR LOWER(a.action) = LOWER(:action))
        AND (:hasResourceType = false OR LOWER(a.resourceType) = LOWER(:resourceType))
        AND (:hasActor = false OR a.actorUserId = :actorUserId)
        AND (:hasResourceId = false OR LOWER(a.resourceId) LIKE LOWER(CONCAT('%', :resourceId, '%')))
        AND a.createdAt >= :fromTs
        AND a.createdAt <= :toTs
      ORDER BY a.createdAt DESC
      """)
  Page<AuditLogEntity> searchAdmin(
      @Param("hasAction") boolean hasAction,
      @Param("action") String action,
      @Param("hasResourceType") boolean hasResourceType,
      @Param("resourceType") String resourceType,
      @Param("hasActor") boolean hasActor,
      @Param("actorUserId") UUID actorUserId,
      @Param("hasResourceId") boolean hasResourceId,
      @Param("resourceId") String resourceId,
      @Param("fromTs") Instant fromTs,
      @Param("toTs") Instant toTs,
      Pageable pageable);
}
