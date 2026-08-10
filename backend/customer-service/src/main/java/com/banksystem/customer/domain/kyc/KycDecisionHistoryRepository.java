package com.banksystem.customer.domain.kyc;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KycDecisionHistoryRepository extends JpaRepository<KycDecisionHistoryEntity, UUID> {
  List<KycDecisionHistoryEntity> findByCaseIdOrderByCreatedAtAsc(UUID caseId);
}
