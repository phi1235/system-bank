package com.banksystem.transaction.domain.virtualaccount;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VirtualAccountRepository extends JpaRepository<VirtualAccountEntity, UUID> {

  Optional<VirtualAccountEntity> findByProviderAndBankBinAndAccountNumber(
      String provider, String bankBin, String accountNumber);

  Optional<VirtualAccountEntity> findByBankBinAndAccountNumber(String bankBin, String accountNumber);

  Optional<VirtualAccountEntity> findByAccountNumber(String accountNumber);

  List<VirtualAccountEntity> findByOrganizationId(UUID organizationId);

  Page<VirtualAccountEntity> findByOrganizationId(UUID organizationId, Pageable pageable);

  @Query("""
      SELECT va FROM VirtualAccountEntity va
      WHERE (:orgId IS NULL OR va.organizationId = :orgId)
        AND (:q IS NULL OR LOWER(va.accountNumber) LIKE LOWER(CONCAT('%', :q, '%'))
             OR LOWER(va.customerReference) LIKE LOWER(CONCAT('%', :q, '%')))
        AND (:status IS NULL OR va.status = :status)
      ORDER BY va.createdAt DESC
      """)
  Page<VirtualAccountEntity> search(
      @Param("orgId") UUID orgId,
      @Param("q") String q,
      @Param("status") VirtualAccountStatus status,
      Pageable pageable);

  boolean existsByProviderAndBankBinAndAccountNumber(String provider, String bankBin, String accountNumber);
}
