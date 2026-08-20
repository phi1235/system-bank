package com.banksystem.transaction.domain.settlement;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementAuditLogRepository extends JpaRepository<SettlementAuditLogEntity, UUID> {
  List<SettlementAuditLogEntity> findBySettlementIdOrderByCreatedAtDesc(UUID settlementId);
}
