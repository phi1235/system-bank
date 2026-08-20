package com.banksystem.transaction.domain.collection;

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

public interface CollectionOrderRepository extends JpaRepository<CollectionOrderEntity, UUID> {

  Optional<CollectionOrderEntity> findByOrganizationIdAndMerchantOrderId(UUID organizationId, String merchantOrderId);

  boolean existsByOrganizationIdAndMerchantOrderId(UUID organizationId, String merchantOrderId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT o FROM CollectionOrderEntity o WHERE o.id = :id")
  Optional<CollectionOrderEntity> findByIdForUpdate(@Param("id") UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT o FROM CollectionOrderEntity o WHERE o.virtualAccountId = :virtualAccountId AND o.status = 'PENDING'")
  List<CollectionOrderEntity> findPendingByVirtualAccountIdForUpdate(@Param("virtualAccountId") UUID virtualAccountId);

  List<CollectionOrderEntity> findByVirtualAccountId(UUID virtualAccountId);

  @Query("""
      SELECT o FROM CollectionOrderEntity o
      WHERE (:orgId IS NULL OR o.organizationId = :orgId)
        AND (:q IS NULL OR LOWER(o.merchantOrderId) LIKE LOWER(CONCAT('%', :q, '%'))
             OR LOWER(o.customerReference) LIKE LOWER(CONCAT('%', :q, '%')))
        AND (:status IS NULL OR o.status = :status)
      ORDER BY o.createdAt DESC
      """)
  Page<CollectionOrderEntity> search(
      @Param("orgId") UUID orgId,
      @Param("q") String q,
      @Param("status") CollectionOrderStatus status,
      Pageable pageable);

  long countByOrganizationIdAndStatus(UUID organizationId, CollectionOrderStatus status);
}
