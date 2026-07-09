package com.banksystem.transaction.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SagaStepLogRepository extends JpaRepository<SagaStepLogEntity, UUID> {
}
