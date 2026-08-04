package com.banksystem.transaction.domain.transfer;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SagaStepLogRepository extends JpaRepository<SagaStepLogEntity, UUID> {

  List<SagaStepLogEntity> findByTransferIdOrderByCreatedAtAsc(UUID transferId);
}
