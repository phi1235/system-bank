package com.banksystem.corporate.domain.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CorporateAuditLogRepository extends JpaRepository<CorporateAuditLogEntity, UUID> {
  List<CorporateAuditLogEntity> findByCorporateIdOrderByCreatedAtDesc(UUID corporateId);
  Page<CorporateAuditLogEntity> findByCorporateIdOrderByCreatedAtDesc(UUID corporateId, Pageable pageable);
}
