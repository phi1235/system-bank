package com.banksystem.transaction.application.forensics;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.common.security.GatewayUser;
import com.banksystem.common.security.SecurityHeaders;
import com.banksystem.transaction.api.dto.ForensicOperationDtos.EvidenceExportResponse;
import com.banksystem.transaction.domain.audit.AuditLogEntity;
import com.banksystem.transaction.domain.audit.AuditLogRepository;
import com.banksystem.transaction.domain.forensics.ForensicExportJobEntity;
import com.banksystem.transaction.domain.forensics.ForensicExportJobRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ForensicExportService {
  private final ForensicExportJobRepository repository;
  private final ForensicExportWorker worker;
  private final ForensicArtifactStorage storage;
  private final AuditLogRepository auditRepository;
  private final Clock clock;
  private final Duration retention;

  public ForensicExportService(
      ForensicExportJobRepository repository,
      ForensicExportWorker worker,
      ForensicArtifactStorage storage,
      AuditLogRepository auditRepository,
      Clock clock,
      @Value("${bank.forensics.export-retention}") Duration retention) {
    this.repository = repository;
    this.worker = worker;
    this.storage = storage;
    this.auditRepository = auditRepository;
    this.clock = clock;
    this.retention = retention;
  }

  @Transactional
  public EvidenceExportResponse create(UUID caseId, String reason, GatewayUser actor) {
    Instant now = clock.instant();
    ForensicExportJobEntity entity = ForensicExportJobEntity.pending(
        UUID.randomUUID(), caseId, actor.userId(), reason.trim(), now, now.plus(retention));
    repository.save(entity);
    auditRepository.save(AuditLogEntity.of(
        actor.userId(), "FORENSIC_EXPORT_REQUESTED", "FORENSIC_EXPORT", entity.getId().toString(),
        "unknown", "caseId=" + caseId + ",reason=" + reason.trim()));
    afterCommit(() -> worker.generate(entity.getId()));
    return toResponse(entity);
  }

  @Transactional(readOnly = true)
  public EvidenceExportResponse get(UUID id, GatewayUser actor) {
    ForensicExportJobEntity entity = require(id);
    requireAccess(entity, actor);
    return toResponse(entity);
  }

  @Transactional
  public ForensicArtifactStorage.StoredArtifact download(UUID id, GatewayUser actor) {
    ForensicExportJobEntity entity = require(id);
    requireAccess(entity, actor);
    if (!"COMPLETED".equals(entity.getStatus()) || entity.getExpiresAt().isBefore(clock.instant())) {
      throw new BusinessException("FORENSIC_EXPORT_NOT_READY", "Evidence export is not available");
    }
    auditRepository.save(AuditLogEntity.of(
        actor.userId(), "FORENSIC_EXPORT_DOWNLOADED", "FORENSIC_EXPORT", id.toString(),
        "unknown", "caseId=" + entity.getCaseId()));
    return storage.get(entity.getStorageUri());
  }

  private void requireAccess(ForensicExportJobEntity entity, GatewayUser actor) {
    if (!entity.getRequestedBy().equals(actor.userId())
        && !actor.hasPermission(SecurityHeaders.PERM_FORENSICS_ADMIN)) {
      throw new BusinessException(
          "FORBIDDEN_FORENSICS_ACCESS", "Evidence export belongs to another user");
    }
  }

  private ForensicExportJobEntity require(UUID id) {
    return repository.findById(id).orElseThrow(() ->
        new BusinessException("FORENSIC_EXPORT_NOT_FOUND", "Evidence export job not found"));
  }

  private EvidenceExportResponse toResponse(ForensicExportJobEntity entity) {
    return new EvidenceExportResponse(
        entity.getId(), entity.getCaseId(), entity.getStatus(), entity.getSensitivity(),
        entity.getPackageSha256(), entity.getErrorDetail(), entity.getCreatedAt(),
        entity.getCompletedAt(), entity.getExpiresAt());
  }

  private void afterCommit(Runnable task) {
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override public void afterCommit() { task.run(); }
    });
  }
}
