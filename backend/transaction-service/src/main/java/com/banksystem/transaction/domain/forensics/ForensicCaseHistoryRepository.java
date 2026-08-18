package com.banksystem.transaction.domain.forensics;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ForensicCaseHistoryRepository
    extends JpaRepository<ForensicCaseHistoryEntity, UUID> {
  Page<ForensicCaseHistoryEntity> findByCaseIdOrderByCreatedAtDesc(UUID caseId, Pageable pageable);
}
