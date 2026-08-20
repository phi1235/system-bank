package com.banksystem.corporate.application.audit;

import com.banksystem.corporate.domain.audit.CorporateAuditLogEntity;
import com.banksystem.corporate.domain.audit.CorporateAuditLogRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CorporateAuditService {

  private static final Logger log = LoggerFactory.getLogger(CorporateAuditService.class);
  private final CorporateAuditLogRepository auditLogRepository;

  public CorporateAuditService(CorporateAuditLogRepository auditLogRepository) {
    this.auditLogRepository = auditLogRepository;
  }

  @Transactional
  public void log(
      UUID corporateId,
      UUID userId,
      String action,
      String entityType,
      String entityId,
      String details) {
    logWithIp(corporateId, userId, action, entityType, entityId, details, "127.0.0.1");
  }

  @Transactional
  public void logWithIp(
      UUID corporateId,
      UUID userId,
      String action,
      String entityType,
      String entityId,
      String details,
      String ipAddress) {
    CorporateAuditLogEntity entry = CorporateAuditLogEntity.of(
        corporateId, userId, action, entityType, entityId, details, ipAddress);
    auditLogRepository.save(entry);
    log.info("[CORP-AUDIT] Corp=[{}] User=[{}] Action=[{}] Entity=[{}:{}] Details={}",
        corporateId, userId, action, entityType, entityId, details);
  }

  @Transactional(readOnly = true)
  public List<CorporateAuditLogEntity> listAuditLogs(UUID corporateId) {
    return auditLogRepository.findByCorporateIdOrderByCreatedAtDesc(corporateId);
  }

  @Transactional(readOnly = true)
  public Page<CorporateAuditLogEntity> listAuditLogsPaged(UUID corporateId, Pageable pageable) {
    return auditLogRepository.findByCorporateIdOrderByCreatedAtDesc(corporateId, pageable);
  }
}
