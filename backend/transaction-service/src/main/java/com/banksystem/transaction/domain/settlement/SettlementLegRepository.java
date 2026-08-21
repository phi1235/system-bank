package com.banksystem.transaction.domain.settlement;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SettlementLegRepository extends JpaRepository<SettlementLegEntity, UUID> {
  @Query("SELECT l FROM SettlementLegEntity l WHERE l.settlement.id = :settlementId")
  List<SettlementLegEntity> findBySettlementId(@Param("settlementId") UUID settlementId);

  List<SettlementLegEntity> findBySettlement_Id(UUID settlementId);
}
