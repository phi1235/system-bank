package com.banksystem.account.domain.ledger;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialEventRepository extends JpaRepository<FinancialEventEntity, UUID> {
  List<FinancialEventEntity> findByTransactionIdOrderByOccurredAtAsc(UUID transactionId);
  List<FinancialEventEntity> findByAggregateTypeAndAggregateIdOrderBySequenceNoAsc(
      String aggregateType, UUID aggregateId);
}
