package com.banksystem.transaction.domain.settlement;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementLegRepository extends JpaRepository<SettlementLegEntity, UUID> {
  List<SettlementLegEntity> findBySettlementId(UUID settlementId);
}
