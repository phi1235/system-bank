package com.banksystem.customer.domain.support;
import com.banksystem.customer.application.customer.*;
import com.banksystem.customer.application.support.*;
import com.banksystem.customer.application.dashboard.*;
import com.banksystem.customer.domain.customer.*;
import com.banksystem.customer.domain.support.*;
import com.banksystem.customer.api.dto.*;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupportTicketRepository extends JpaRepository<SupportTicketEntity, UUID> {

  Page<SupportTicketEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

  Optional<SupportTicketEntity> findByIdAndUserId(UUID id, UUID userId);

  long countByUserIdAndStatus(UUID userId, String status);

  @Query(
      """
      SELECT t FROM SupportTicketEntity t
      WHERE (:status IS NULL OR t.status = :status)
        AND (:category IS NULL OR t.category = :category)
        AND (
          :q IS NULL OR :q = ''
          OR LOWER(t.subject) LIKE LOWER(CONCAT('%', :q, '%'))
          OR LOWER(t.body) LIKE LOWER(CONCAT('%', :q, '%'))
          OR LOWER(COALESCE(t.requesterEmail, '')) LIKE LOWER(CONCAT('%', :q, '%'))
        )
      ORDER BY t.createdAt DESC
      """)
  Page<SupportTicketEntity> search(
      @Param("status") String status,
      @Param("category") String category,
      @Param("q") String q,
      Pageable pageable);
}
