package com.banksystem.corporate.domain.approval;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApprovalInstanceRepository extends JpaRepository<ApprovalInstanceEntity, UUID> {
  Optional<ApprovalInstanceEntity> findByBatchId(UUID batchId);
}
