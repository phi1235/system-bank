package com.banksystem.auth.domain.b2b;

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
public interface B2bAccountConsentRepository extends JpaRepository<B2bAccountConsentEntity, UUID> {

  List<B2bAccountConsentEntity> findByClientId(String clientId);

  List<B2bAccountConsentEntity> findByCustomerId(UUID customerId);

  Optional<B2bAccountConsentEntity> findByClientIdAndAccountNumber(String clientId, String accountNumber);

  Optional<B2bAccountConsentEntity> findByClientIdAndAccountNumberAndStatus(String clientId, String accountNumber, String status);

  @Query("SELECT c FROM B2bAccountConsentEntity c WHERE " +
         "(:hasClientId = false OR c.clientId = :clientId) AND " +
         "(:hasCustomerId = false OR c.customerId = :customerId) AND " +
         "(:hasStatus = false OR c.status = :status) AND " +
         "(:hasAccountNumber = false OR c.accountNumber LIKE CONCAT('%', :accountNumber, '%'))")
  List<B2bAccountConsentEntity> searchConsentsList(
      @Param("hasClientId") boolean hasClientId,
      @Param("clientId") String clientId,
      @Param("hasCustomerId") boolean hasCustomerId,
      @Param("customerId") UUID customerId,
      @Param("hasStatus") boolean hasStatus,
      @Param("status") String status,
      @Param("hasAccountNumber") boolean hasAccountNumber,
      @Param("accountNumber") String accountNumber);

  @Query("SELECT c FROM B2bAccountConsentEntity c WHERE " +
         "(:hasClientId = false OR c.clientId = :clientId) AND " +
         "(:hasCustomerId = false OR c.customerId = :customerId) AND " +
         "(:hasStatus = false OR c.status = :status) AND " +
         "(:hasAccountNumber = false OR c.accountNumber LIKE CONCAT('%', :accountNumber, '%'))")
  Page<B2bAccountConsentEntity> searchConsents(
      @Param("hasClientId") boolean hasClientId,
      @Param("clientId") String clientId,
      @Param("hasCustomerId") boolean hasCustomerId,
      @Param("customerId") UUID customerId,
      @Param("hasStatus") boolean hasStatus,
      @Param("status") String status,
      @Param("hasAccountNumber") boolean hasAccountNumber,
      @Param("accountNumber") String accountNumber,
      Pageable pageable);
}
