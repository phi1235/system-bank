package com.banksystem.customer.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<CustomerEntity, UUID> {

  Optional<CustomerEntity> findByEmailIgnoreCase(String email);

  /**
   * Admin customer search. Callers pass boolean flags so optional filters never rely on
   * untyped NULL string binds for equality checks.
   */
  @Query("""
      SELECT c FROM CustomerEntity c
      WHERE (:hasQ = false
          OR LOWER(c.fullName) LIKE LOWER(CONCAT('%', :q, '%'))
          OR LOWER(c.email) LIKE LOWER(CONCAT('%', :q, '%')))
        AND (:hasKyc = false OR c.kycStatus = :kycStatus)
      ORDER BY c.updatedAt DESC
      """)
  Page<CustomerEntity> search(
      @Param("hasQ") boolean hasQ,
      @Param("q") String q,
      @Param("hasKyc") boolean hasKyc,
      @Param("kycStatus") String kycStatus,
      Pageable pageable);
}
