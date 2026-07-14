package com.banksystem.auth.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTicketRepository extends JpaRepository<PasswordResetTicketEntity, UUID> {
  Page<PasswordResetTicketEntity> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

  Page<PasswordResetTicketEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

  boolean existsByUserIdAndStatus(UUID userId, String status);

  List<PasswordResetTicketEntity> findByUserIdAndStatus(UUID userId, String status);
}
