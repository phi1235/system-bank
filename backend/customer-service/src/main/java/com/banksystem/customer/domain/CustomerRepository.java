package com.banksystem.customer.domain;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<CustomerEntity, UUID> {

  @Query("""
      SELECT c FROM CustomerEntity c
      WHERE (:q IS NULL OR :q = ''
        OR LOWER(c.fullName) LIKE LOWER(CONCAT('%', :q, '%'))
        OR LOWER(c.email) LIKE LOWER(CONCAT('%', :q, '%')))
      """)
  Page<CustomerEntity> search(@Param("q") String q, Pageable pageable);
}
