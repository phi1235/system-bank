package com.banksystem.transaction.domain.settlement;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SettlementRepository extends JpaRepository<SettlementEntity, UUID> {

  Optional<SettlementEntity> findByCollectionOrderId(UUID collectionOrderId);

  Optional<SettlementEntity> findByCommandId(String commandId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT s FROM SettlementEntity s WHERE s.collectionOrderId = :orderId")
  Optional<SettlementEntity> findByCollectionOrderIdForUpdate(@Param("orderId") UUID orderId);

  boolean existsByCollectionOrderId(UUID collectionOrderId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT s FROM SettlementEntity s WHERE s.id = :id")
  Optional<SettlementEntity> findByIdForUpdate(@Param("id") UUID id);

  @Query("""
      SELECT s FROM SettlementEntity s
      WHERE (:orgId IS NULL OR s.organizationId = :orgId)
        AND (:status IS NULL OR s.status = :status)
      ORDER BY s.createdAt DESC
      """)
  Page<SettlementEntity> search(
      @Param("orgId") UUID orgId,
      @Param("status") SettlementStatus status,
      Pageable pageable);

  long countByOrganizationIdAndStatus(UUID organizationId, SettlementStatus status);
}
