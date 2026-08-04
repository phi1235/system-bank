package com.banksystem.transaction.application;

import com.banksystem.common.api.PageResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.api.dto.TransferDtos.AdminAuditFilterRequest;
import com.banksystem.transaction.api.dto.TransferDtos.AuditResponse;
import com.banksystem.transaction.application.query.AuditListQuery;
import com.banksystem.transaction.domain.AuditLogEntity;
import com.banksystem.transaction.domain.AuditLogRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Staff ops for transaction audit_logs: filtered list + detail.
 */
@Service
public class AuditAdminService {

  private final AuditLogRepository repository;
  private final JdbcTemplate jdbcTemplate;

  public AuditAdminService(AuditLogRepository repository, JdbcTemplate jdbcTemplate) {
    this.repository = repository;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional(readOnly = true)
  public PageResponse<AuditResponse> list(AuditListQuery query) {
    PageRequest pageable = PageRequest.of(query.page(), query.size());
    Slice<AuditLogEntity> slice =
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
    List<AuditResponse> items = slice.getContent().stream().map(this::toResponse).toList();
    long estimatedTotal = estimatedRowCount("audit_logs");
    int totalPages = (int) Math.ceil((double) estimatedTotal / query.size());
    return new PageResponse<>(
        items,
        query.page(),
        query.size(),
        estimatedTotal,
        totalPages);
  }

  @Transactional(readOnly = true)
  public Object list(AdminAuditFilterRequest req) {
    AuditListQuery query = AuditListQuery.of(
        req.action(), req.resourceType(), req.actorUserId(), req.resourceId(), req.from(), req.to(), req.page(), req.size());
    if (Boolean.TRUE.equals(req.noCount())) {
      return listSlice(query);
    }
    return list(query);
  }

  @Transactional(readOnly = true)
  public Object list(AuditListQuery query, boolean noCount) {
    if (noCount) {
      return listSlice(query);
    }
    return list(query);
  }

  @Transactional(readOnly = true)
  public List<AuditResponse> listSlice(AuditListQuery query) {
    PageRequest pageable = PageRequest.of(query.page(), query.size());
    Slice<AuditLogEntity> slice =
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
            .orElseThrow(() -> new BusinessException("AUDIT_NOT_FOUND", "Audit log not found"));
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

  private long estimatedRowCount(String tableName) {
    Long estimate = jdbcTemplate.queryForObject(
        "SELECT n_live_tup FROM pg_stat_user_tables WHERE relname = ?",
        Long.class, tableName);
    return (estimate != null && estimate > 0) ? estimate : 0;
  }
}
