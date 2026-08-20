package com.banksystem.transaction.domain.openbanking;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IsoPaymentMessageRepository extends JpaRepository<IsoPaymentMessageEntity, UUID> {

  Optional<IsoPaymentMessageEntity> findByMessageId(String messageId);

  boolean existsByMessageId(String messageId);

  @Query("SELECT m FROM IsoPaymentMessageEntity m WHERE " +
         "(:clientId IS NULL OR m.clientId = :clientId) AND " +
         "(:status IS NULL OR m.overallStatus = :status) AND " +
         "(:messageType IS NULL OR m.messageType = :messageType) " +
         "ORDER BY m.createdAt DESC")
  Page<IsoPaymentMessageEntity> searchMessages(
      @Param("clientId") String clientId,
      @Param("status") String status,
      @Param("messageType") String messageType,
      Pageable pageable);
}
