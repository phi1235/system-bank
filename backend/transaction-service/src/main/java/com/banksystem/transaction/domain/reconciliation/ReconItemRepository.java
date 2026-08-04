package com.banksystem.transaction.domain.reconciliation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconItemRepository extends JpaRepository<ReconItemEntity, UUID> {

  List<ReconItemEntity> findByRunIdOrderByKindAscTransferIdAsc(UUID runId);
}
