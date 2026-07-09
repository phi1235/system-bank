package com.banksystem.transaction.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransferOrderRepository extends JpaRepository<TransferOrderEntity, UUID> {
  Optional<TransferOrderEntity> findByIdempotencyKey(String idempotencyKey);

  Page<TransferOrderEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

  @Query("""
      SELECT t FROM TransferOrderEntity t
      WHERE (:status IS NULL OR t.status = :status)
      ORDER BY t.createdAt DESC
      """)
  Page<TransferOrderEntity> adminSearch(@Param("status") TransferStatus status, Pageable pageable);
}
