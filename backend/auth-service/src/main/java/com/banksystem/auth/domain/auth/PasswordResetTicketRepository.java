package com.banksystem.auth.domain.auth;
import static com.banksystem.auth.api.dto.AuthDtos.*;
import static com.banksystem.auth.api.dto.PasswordResetDtos.*;
import static com.banksystem.auth.api.dto.RbacDtos.*;
import com.banksystem.auth.application.auth.*;
import com.banksystem.auth.application.rbac.*;
import com.banksystem.auth.domain.auth.*;
import com.banksystem.auth.domain.rbac.*;
import com.banksystem.auth.api.dto.*;

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
