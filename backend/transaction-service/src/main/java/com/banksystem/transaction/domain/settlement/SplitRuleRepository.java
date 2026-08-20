package com.banksystem.transaction.domain.settlement;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SplitRuleRepository extends JpaRepository<SplitRuleEntity, UUID> {
  List<SplitRuleEntity> findByOrganizationId(UUID organizationId);
  List<SplitRuleEntity> findByOrganizationIdAndStatus(UUID organizationId, String status);
}
