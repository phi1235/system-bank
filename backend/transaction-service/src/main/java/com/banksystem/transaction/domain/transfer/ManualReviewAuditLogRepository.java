package com.banksystem.transaction.domain.transfer;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ManualReviewAuditLogRepository extends JpaRepository<ManualReviewAuditLogEntity, UUID> {

  List<ManualReviewAuditLogEntity> findByTransferIdOrderByCreatedAtDesc(UUID transferId);
}
