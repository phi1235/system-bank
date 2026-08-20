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
         "(:clientId IS NULL OR c.clientId = :clientId) AND " +
         "(:customerId IS NULL OR c.customerId = :customerId) AND " +
         "(:status IS NULL OR c.status = :status) AND " +
         "(:accountNumber IS NULL OR c.accountNumber LIKE CONCAT('%', :accountNumber, '%'))")
  Page<B2bAccountConsentEntity> searchConsents(
      @Param("clientId") String clientId,
      @Param("customerId") UUID customerId,
      @Param("status") String status,
      @Param("accountNumber") String accountNumber,
      Pageable pageable);
}
