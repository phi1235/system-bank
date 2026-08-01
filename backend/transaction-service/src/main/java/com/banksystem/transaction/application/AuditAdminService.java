package com.banksystem.transaction.application;

import com.banksystem.common.api.PageResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.api.dto.TransferDtos.AuditResponse;
import com.banksystem.transaction.application.query.AuditListQuery;
import com.banksystem.transaction.domain.AuditLogEntity;
import com.banksystem.transaction.domain.AuditLogRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Staff ops for transaction audit_logs: filtered list + detail.
 */
@Service
public class AuditAdminService {

  private final AuditLogRepository repository;

  public AuditAdminService(AuditLogRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public PageResponse<AuditResponse> list(AuditListQuery query) {
    PageRequest pageable = PageRequest.of(query.page(), query.size());
    Page<AuditLogEntity> page =
        repository.searchAdmin(
            query.hasAction(),
            query.action() == null ? "" : query.action(),
            query.hasResourceType(),
            query.resourceType() == null ? "" : query.resourceType(),
            query.hasActor(),
            query.actorUserId() == null ? new UUID(0L, 0L) : query.actorUserId(),
            query.hasResourceId(),
            query.resourceId() == null ? "" : query.resourceId(),
            query.from(),
            query.to(),
            pageable);
    List<AuditResponse> items = page.getContent().stream().map(this::toResponse).toList();
    return new PageResponse<>(
        items,
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }

  @Transactional(readOnly = true)
  public List<AuditResponse> listSlice(AuditListQuery query) {
    PageRequest pageable = PageRequest.of(query.page(), query.size());
    org.springframework.data.domain.Slice<AuditLogEntity> slice =
        repository.searchAdminSlice(
            query.hasAction(),
            query.action() == null ? "" : query.action(),
            query.hasResourceType(),
            query.resourceType() == null ? "" : query.resourceType(),
            query.hasActor(),
            query.actorUserId() == null ? new UUID(0L, 0L) : query.actorUserId(),
            query.hasResourceId(),
            query.resourceId() == null ? "" : query.resourceId(),
            query.from(),
            query.to(),
            pageable);
    return slice.getContent().stream().map(this::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public AuditResponse get(UUID id) {
    AuditLogEntity entity =
        repository
            .findById(id)
            .orElseThrow(
                () ->
                    new BusinessException(
                        "AUDIT_NOT_FOUND", "Audit log not found", HttpStatus.NOT_FOUND));
    return toResponse(entity);
  }

  private AuditResponse toResponse(AuditLogEntity e) {
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
