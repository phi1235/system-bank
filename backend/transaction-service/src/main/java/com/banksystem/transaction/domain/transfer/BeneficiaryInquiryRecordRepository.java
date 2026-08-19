package com.banksystem.transaction.domain.transfer;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BeneficiaryInquiryRecordRepository extends JpaRepository<BeneficiaryInquiryRecordEntity, UUID> {

  Optional<BeneficiaryInquiryRecordEntity> findByInquiryId(String inquiryId);

  Optional<BeneficiaryInquiryRecordEntity> findTopByUserIdAndBankBinAndAccountNumberHmacAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
      UUID userId, String bankBin, String accountNumberHmac, String status, Instant now);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE BeneficiaryInquiryRecordEntity r "
          + "SET r.consumedAt = :now, r.status = 'CONSUMED' "
          + "WHERE r.inquiryId = :inquiryId "
          + "AND r.userId = :userId "
          + "AND r.status = 'VERIFIED' "
          + "AND r.consumedAt IS NULL "
          + "AND r.expiresAt > :now"
  )
  int atomicConsume(
      @Param("inquiryId") String inquiryId,
      @Param("userId") UUID userId,
      @Param("now") Instant now
  );
}
