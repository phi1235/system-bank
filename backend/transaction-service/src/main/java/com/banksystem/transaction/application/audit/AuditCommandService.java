package com.banksystem.transaction.application.audit;

import com.banksystem.transaction.api.dto.TransferDtos.AuditResponse;
import com.banksystem.transaction.domain.audit.AuditLogEntity;
import com.banksystem.transaction.domain.audit.AuditLogRepository;
import java.util.UUID;
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

  public AuditCommandService(AuditLogRepository repository) {
    this.repository = repository;
  }

  /**
   * Persists an audit record in a transactional boundary.
   */
  @Transactional
  public AuditResponse recordInternalAudit(CreateAuditLogCommand command) {
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
