package com.banksystem.transaction.domain.risk;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskRuleRepository extends JpaRepository<RiskRuleEntity, UUID> {
  List<RiskRuleEntity> findByEnabledTrueOrderByPriorityAsc();
  Optional<RiskRuleEntity> findByCode(String code);
}
