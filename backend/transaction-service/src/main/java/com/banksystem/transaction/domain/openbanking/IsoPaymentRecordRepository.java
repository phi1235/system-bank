package com.banksystem.transaction.domain.openbanking;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IsoPaymentRecordRepository extends JpaRepository<IsoPaymentRecordEntity, UUID> {

  List<IsoPaymentRecordEntity> findByMessageId(String messageId);

  Optional<IsoPaymentRecordEntity> findByClientIdAndEndToEndId(String clientId, String endToEndId);

  boolean existsByClientIdAndEndToEndId(String clientId, String endToEndId);

  @Query("SELECT r FROM IsoPaymentRecordEntity r WHERE " +
         "(:clientId IS NULL OR r.clientId = :clientId) AND " +
         "(:messageId IS NULL OR r.messageId = :messageId) AND " +
         "(:status IS NULL OR r.status = :status) " +
         "ORDER BY r.createdAt DESC")
  Page<IsoPaymentRecordEntity> searchRecords(
      @Param("clientId") String clientId,
      @Param("messageId") String messageId,
      @Param("status") String status,
      Pageable pageable);
}
