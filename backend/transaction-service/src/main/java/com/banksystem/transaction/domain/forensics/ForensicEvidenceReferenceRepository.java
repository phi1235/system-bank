package com.banksystem.transaction.domain.forensics;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ForensicEvidenceReferenceRepository
    extends JpaRepository<ForensicEvidenceReferenceEntity, UUID> {
  Page<ForensicEvidenceReferenceEntity> findByCaseIdOrderByCapturedAtDesc(
      UUID caseId, Pageable pageable);
}
