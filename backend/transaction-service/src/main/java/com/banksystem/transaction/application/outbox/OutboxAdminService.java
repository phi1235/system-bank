package com.banksystem.transaction.application.outbox;

import com.banksystem.common.api.PageResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.api.dto.OutboxDtos.AdminOutboxFilterRequest;
import com.banksystem.transaction.api.dto.OutboxDtos.OutboxCountsResponse;
import com.banksystem.transaction.api.dto.OutboxDtos.OutboxEventResponse;
import com.banksystem.transaction.application.outbox.OutboxListQuery;
import com.banksystem.transaction.domain.outbox.OutboxEventEntity;
import com.banksystem.transaction.domain.outbox.OutboxEventRepository;
import com.banksystem.transaction.domain.outbox.OutboxStatus;
import com.banksystem.transaction.infrastructure.outbox.OutboxMetrics;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Staff ops for outbox: inspect DEAD/PENDING/PUBLISHED rows and re-queue DEAD for publish.
 */
@Service
public class OutboxAdminService {

  private final OutboxEventRepository repository;
  private final OutboxMetrics metrics;
  private final Clock clock;
  private final JdbcTemplate jdbcTemplate;

  public OutboxAdminService(OutboxEventRepository repository, OutboxMetrics metrics, Clock clock, JdbcTemplate jdbcTemplate) {
    this.repository = repository;
    this.metrics = metrics;
    this.clock = clock;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional(readOnly = true)
  public PageResponse<OutboxEventResponse> list(AdminOutboxFilterRequest req) {
    OutboxListQuery query = OutboxListQuery.of(
        req.status(), req.eventType(), req.eventId(), req.aggregateId(), req.q(), req.from(), req.to(), req.page(), req.size());
    return list(query);
  }

  @Transactional(readOnly = true)
  public PageResponse<OutboxEventResponse> list(OutboxListQuery query) {
    PageRequest pageable = PageRequest.of(query.page(), query.size());
    Slice<OutboxEventEntity> slice =
        repository.searchAdminSlice(
            query.status().name(),
            query.hasEventType(),
            query.hasEventType() ? query.eventType() : "",
            query.hasEventId(),
            query.hasEventId() ? query.eventId() : new UUID(0L, 0L),
            query.hasAggregateId(),
            query.hasAggregateId() ? query.aggregateId() : new UUID(0L, 0L),
            query.hasQ(),
            query.hasQ() ? query.q() : "",
            query.from(),
            query.to(),
            pageable);
    List<OutboxEventResponse> items =
        slice.getContent().stream().map(e -> toResponse(e, false)).toList();
    long estimatedTotal = estimatedRowCount("outbox_events");
    int totalPages = (int) Math.ceil((double) estimatedTotal / query.size());
    return new PageResponse<>(
        items,
        query.page(),
        query.size(),
        estimatedTotal,
        totalPages);
  }

  @Transactional(readOnly = true)
  public OutboxEventResponse get(UUID id) {
    return toResponse(require(id), true);
  }

  @Transactional(readOnly = true)
  public OutboxCountsResponse counts() {
    return new OutboxCountsResponse(
        repository.countByStatus(OutboxStatus.PENDING.name()),
        repository.countByStatus(OutboxStatus.PUBLISHED.name()),
        repository.countByStatus(OutboxStatus.DEAD.name()));
  }

  /**
   * Re-queue a DEAD event: status=PENDING, attempt_count=0, next_attempt_at=now.
   * Poller will pick it up on the next due claim.
   */
  @Transactional
  public OutboxEventResponse replay(UUID id) {
    OutboxEventEntity event = require(id);
    if (!OutboxStatus.DEAD.name().equals(event.getStatus())) {
      throw new BusinessException(
          "OUTBOX_NOT_DEAD",
          "Only DEAD outbox events can be replayed (current=" + event.getStatus() + ")");
    }
    event.markForReplay(clock.instant());
    OutboxEventEntity saved = repository.save(event);
    metrics.incrementReplayed();
    return toResponse(saved, true);
  }

  private OutboxEventEntity require(UUID id) {
    return repository.findById(id)
        .orElseThrow(() -> new BusinessException(
            "OUTBOX_NOT_FOUND", "Outbox event not found"));
  }

  private OutboxEventResponse toResponse(OutboxEventEntity e, boolean includePayload) {
    return new OutboxEventResponse(
        e.getId().toString(),
        e.getAggregateType(),
        e.getAggregateId().toString(),
        e.getEventType(),
        e.getStatus(),
        e.getAttemptCount(),
        e.getNextAttemptAt(),
        e.getCreatedAt(),
        e.getPublishedAt(),
        e.getLastError(),
        includePayload ? e.getPayload() : null);
  }

  private long estimatedRowCount(String tableName) {
    Long estimate = jdbcTemplate.queryForObject(
        "SELECT n_live_tup FROM pg_stat_user_tables WHERE relname = ?",
        Long.class, tableName);
    if (estimate == null || estimate <= 10000) {
      Long exact = jdbcTemplate.queryForObject(
          "SELECT COUNT(*) FROM " + tableName, Long.class);
      return exact != null ? exact : 0;
    }
    return estimate;
  }
}
