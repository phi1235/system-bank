package com.banksystem.auth.domain.b2b;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface B2bClientApplicationRepository extends JpaRepository<B2bClientApplicationEntity, UUID> {

  Optional<B2bClientApplicationEntity> findByClientId(String clientId);

  boolean existsByClientId(String clientId);

  @Query("SELECT c FROM B2bClientApplicationEntity c WHERE " +
         "(:status IS NULL OR c.status = :status) AND " +
         "(:q IS NULL OR LOWER(c.clientId) LIKE LOWER(CONCAT('%', :q, '%')) " +
         "OR LOWER(c.clientName) LIKE LOWER(CONCAT('%', :q, '%')) " +
         "OR LOWER(c.organizationTaxCode) LIKE LOWER(CONCAT('%', :q, '%')))")
  Page<B2bClientApplicationEntity> searchClients(
      @Param("status") String status,
      @Param("q") String q,
      Pageable pageable);
}
