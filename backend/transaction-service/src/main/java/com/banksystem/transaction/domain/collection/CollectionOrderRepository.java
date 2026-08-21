package com.banksystem.transaction.domain.collection;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
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
      WHERE (:hasOrgId = false OR o.organizationId = :orgId)
        AND (:hasQ = false OR (
            LOWER(o.merchantOrderId) LIKE LOWER(CONCAT('%', :q, '%'))
            OR (o.customerReference IS NOT NULL AND LOWER(o.customerReference) LIKE LOWER(CONCAT('%', :q, '%')))
        ))
        AND (:hasStatus = false OR o.status = :status)
      ORDER BY o.createdAt DESC
      """)
  List<CollectionOrderEntity> searchList(
      @Param("hasOrgId") boolean hasOrgId,
      @Param("orgId") UUID orgId,
      @Param("hasQ") boolean hasQ,
      @Param("q") String q,
      @Param("hasStatus") boolean hasStatus,
      @Param("status") CollectionOrderStatus status);

  @Query("""
      SELECT o FROM CollectionOrderEntity o
      WHERE (:hasOrgId = false OR o.organizationId = :orgId)
        AND (:hasQ = false OR (
            LOWER(o.merchantOrderId) LIKE LOWER(CONCAT('%', :q, '%'))
            OR (o.customerReference IS NOT NULL AND LOWER(o.customerReference) LIKE LOWER(CONCAT('%', :q, '%')))
        ))
        AND (:hasStatus = false OR o.status = :status)
      ORDER BY o.createdAt DESC
      """)
  Page<CollectionOrderEntity> search(
      @Param("hasOrgId") boolean hasOrgId,
      @Param("orgId") UUID orgId,
      @Param("hasQ") boolean hasQ,
      @Param("q") String q,
      @Param("hasStatus") boolean hasStatus,
      @Param("status") CollectionOrderStatus status,
      Pageable pageable);

  long countByOrganizationIdAndStatus(UUID organizationId, CollectionOrderStatus status);

  @Query("SELECT COALESCE(SUM(o.paidAmount), 0) FROM CollectionOrderEntity o WHERE o.organizationId = :orgId AND o.status = :status")
  BigDecimal sumPaidAmountByOrganizationIdAndStatus(
      @Param("orgId") UUID orgId,
      @Param("status") CollectionOrderStatus status);
}
