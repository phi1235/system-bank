package com.banksystem.transaction.domain.settlement;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SplitRuleItemRepository extends JpaRepository<SplitRuleItemEntity, UUID> {
  @Query("SELECT i FROM SplitRuleItemEntity i WHERE i.splitRule.id = :splitRuleId ORDER BY i.priority ASC")
  List<SplitRuleItemEntity> findBySplitRuleIdOrderByPriorityAsc(@Param("splitRuleId") UUID splitRuleId);

  List<SplitRuleItemEntity> findBySplitRule_IdOrderByPriorityAsc(UUID splitRuleId);
}
