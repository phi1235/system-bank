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
public interface B2bClientApplicationRepository extends JpaRepository<B2bClientApplicationEntity, UUID> {

  Optional<B2bClientApplicationEntity> findByClientId(String clientId);

  boolean existsByClientId(String clientId);

  @Query("SELECT c FROM B2bClientApplicationEntity c WHERE " +
         "(:hasStatus = false OR c.status = :status) AND " +
         "(:hasQ = false OR LOWER(c.clientId) LIKE LOWER(CONCAT('%', :q, '%')) " +
         "OR LOWER(c.clientName) LIKE LOWER(CONCAT('%', :q, '%')) " +
         "OR LOWER(c.organizationTaxCode) LIKE LOWER(CONCAT('%', :q, '%')))")
  List<B2bClientApplicationEntity> searchClientsList(
      @Param("hasStatus") boolean hasStatus,
      @Param("status") String status,
      @Param("hasQ") boolean hasQ,
      @Param("q") String q);

  @Query("SELECT c FROM B2bClientApplicationEntity c WHERE " +
         "(:hasStatus = false OR c.status = :status) AND " +
         "(:hasQ = false OR LOWER(c.clientId) LIKE LOWER(CONCAT('%', :q, '%')) " +
         "OR LOWER(c.clientName) LIKE LOWER(CONCAT('%', :q, '%')) " +
         "OR LOWER(c.organizationTaxCode) LIKE LOWER(CONCAT('%', :q, '%')))")
  Page<B2bClientApplicationEntity> searchClients(
      @Param("hasStatus") boolean hasStatus,
      @Param("status") String status,
      @Param("hasQ") boolean hasQ,
      @Param("q") String q,
      Pageable pageable);
}
