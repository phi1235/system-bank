package com.banksystem.transaction.application;

import com.banksystem.common.api.PageResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.api.dto.OutboxDtos.AdminOutboxFilterRequest;
import com.banksystem.transaction.api.dto.OutboxDtos.OutboxCountsResponse;
import com.banksystem.transaction.api.dto.OutboxDtos.OutboxEventResponse;
import com.banksystem.transaction.application.query.OutboxListQuery;
import com.banksystem.transaction.domain.OutboxEventEntity;
import com.banksystem.transaction.domain.OutboxEventRepository;
import com.banksystem.transaction.domain.OutboxStatus;
import com.banksystem.transaction.infrastructure.outbox.OutboxMetrics;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

  public OutboxAdminService(OutboxEventRepository repository, OutboxMetrics metrics, Clock clock) {
    this.repository = repository;
    this.metrics = metrics;
    this.clock = clock;
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
    Page<OutboxEventEntity> page =
        repository.searchAdmin(
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
        page.getContent().stream().map(e -> toResponse(e, false)).toList();
    return new PageResponse<>(
        items,
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
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
}
