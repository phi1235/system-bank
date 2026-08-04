package com.banksystem.transaction.application;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.common.security.SecretVerifier;
import com.banksystem.transaction.api.dto.TransferDtos.AuditResponse;
import com.banksystem.transaction.domain.AuditLogEntity;
import com.banksystem.transaction.domain.AuditLogRepository;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service managing audit log command operations (Write side of CQRS).
 */
@Service
public class AuditCommandService {

  public record CreateAuditLogCommand(
      UUID actorUserId,
      String action,
      String resourceType,
      String resourceId,
      String ip,
      String metadata) {}

  private final AuditLogRepository repository;
  private final String apiKey;

  public AuditCommandService(
      AuditLogRepository repository,
      @Value("${bank.internal.api-key}") String apiKey) {
    this.repository = repository;
    this.apiKey = apiKey;
  }

  /**
   * Validates internal key and persists audit record in transactional boundary.
   */
  @Transactional
  public AuditResponse recordInternalAudit(String key, CreateAuditLogCommand command) {
    verifyInternalKey(key);
    AuditLogEntity entity = AuditLogEntity.of(
        command.actorUserId(),
        command.action(),
        command.resourceType(),
        command.resourceId(),
        command.ip(),
        command.metadata());
    AuditLogEntity saved = repository.save(entity);
    return mapToResponse(saved);
  }

  private void verifyInternalKey(String key) {
    if (!SecretVerifier.matches(key, apiKey)) {
      throw new BusinessException("FORBIDDEN", "Invalid internal API key");
    }
  }

  private AuditResponse mapToResponse(AuditLogEntity e) {
    return new AuditResponse(
        e.getId().toString(),
        e.getActorUserId() == null ? null : e.getActorUserId().toString(),
        e.getAction(),
        e.getResourceType(),
        e.getResourceId(),
        e.getIp(),
        e.getMetadata(),
        e.getCreatedAt());
  }
}
