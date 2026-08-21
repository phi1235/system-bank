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
      WHERE (:hasOrgId = false OR va.organizationId = :orgId)
        AND (:hasQ = false OR (
            LOWER(va.accountNumber) LIKE LOWER(CONCAT('%', :q, '%'))
            OR (va.customerReference IS NOT NULL AND LOWER(va.customerReference) LIKE LOWER(CONCAT('%', :q, '%')))
        ))
        AND (:hasStatus = false OR va.status = :status)
      ORDER BY va.createdAt DESC
      """)
  List<VirtualAccountEntity> searchList(
      @Param("hasOrgId") boolean hasOrgId,
      @Param("orgId") UUID orgId,
      @Param("hasQ") boolean hasQ,
      @Param("q") String q,
      @Param("hasStatus") boolean hasStatus,
      @Param("status") VirtualAccountStatus status);

  @Query("""
      SELECT va FROM VirtualAccountEntity va
      WHERE (:hasOrgId = false OR va.organizationId = :orgId)
        AND (:hasQ = false OR (
            LOWER(va.accountNumber) LIKE LOWER(CONCAT('%', :q, '%'))
            OR (va.customerReference IS NOT NULL AND LOWER(va.customerReference) LIKE LOWER(CONCAT('%', :q, '%')))
        ))
        AND (:hasStatus = false OR va.status = :status)
      ORDER BY va.createdAt DESC
      """)
  Page<VirtualAccountEntity> search(
      @Param("hasOrgId") boolean hasOrgId,
      @Param("orgId") UUID orgId,
      @Param("hasQ") boolean hasQ,
      @Param("q") String q,
      @Param("hasStatus") boolean hasStatus,
      @Param("status") VirtualAccountStatus status,
      Pageable pageable);

  boolean existsByProviderAndBankBinAndAccountNumber(String provider, String bankBin, String accountNumber);

  long countByOrganizationId(UUID organizationId);

  long countByOrganizationIdAndStatus(UUID organizationId, VirtualAccountStatus status);
}
