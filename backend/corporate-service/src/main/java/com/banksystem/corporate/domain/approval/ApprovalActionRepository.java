package com.banksystem.corporate.domain.approval;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalActionRepository extends JpaRepository<ApprovalActionEntity, UUID> {
  List<ApprovalActionEntity> findByBatchIdOrderByActionTimestampAsc(UUID batchId);
  List<ApprovalActionEntity> findByTaskIdOrderByActionTimestampAsc(UUID taskId);
  boolean existsByTaskIdAndActorId(UUID taskId, UUID actorId);
  boolean existsByBatchIdAndActorId(UUID batchId, UUID actorId);
}
