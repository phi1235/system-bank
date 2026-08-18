package com.banksystem.transaction.domain.forensics;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RemediationProposalRepository extends JpaRepository<RemediationProposalEntity, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM RemediationProposalEntity p WHERE p.id = :id")
  Optional<RemediationProposalEntity> findByIdForUpdate(@Param("id") UUID id);

  List<RemediationProposalEntity> findByCaseIdAndInvestigationCycleOrderByCreatedAtAsc(
      UUID caseId, int investigationCycle);

  List<RemediationProposalEntity> findByCaseIdOrderByCreatedAtAsc(UUID caseId);

  List<RemediationProposalEntity> findByStatus(RemediationProposalStatus status);
}
