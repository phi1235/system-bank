package com.banksystem.transaction.domain.settlement;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SplitRuleItemRepository extends JpaRepository<SplitRuleItemEntity, UUID> {
  List<SplitRuleItemEntity> findBySplitRuleIdOrderByPriorityAsc(UUID splitRuleId);
}
