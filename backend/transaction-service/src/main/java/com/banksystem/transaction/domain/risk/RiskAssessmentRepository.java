package com.banksystem.transaction.domain.risk;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskAssessmentRepository extends JpaRepository<RiskAssessmentEntity, UUID> {
  Optional<RiskAssessmentEntity> findByTransferId(UUID transferId);
}
