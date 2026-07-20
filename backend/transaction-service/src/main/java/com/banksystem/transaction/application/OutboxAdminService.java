package com.banksystem.transaction.application;

import com.banksystem.common.api.PageResponse;
import com.banksystem.common.exception.BusinessException;
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
import org.springframework.http.HttpStatus;
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
  public PageResponse<OutboxEventResponse> list(OutboxListQuery query) {
    PageRequest pageable = PageRequest.of(query.page(), query.size());
    Page<OutboxEventEntity> page =
        repository.findByStatusOrderByCreatedAtDesc(query.status().name(), pageable);
    List<OutboxEventResponse> items = page.getContent().stream().map(this::toResponse).toList();
    return new PageResponse<>(
        items,
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }

  @Transactional(readOnly = true)
  public OutboxEventResponse get(UUID id) {
    return toResponse(require(id));
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
          "Only DEAD outbox events can be replayed (current=" + event.getStatus() + ")",
          HttpStatus.UNPROCESSABLE_ENTITY);
    }
    event.markForReplay(clock.instant());
    OutboxEventEntity saved = repository.save(event);
    metrics.incrementReplayed();
    return toResponse(saved);
  }

  private OutboxEventEntity require(UUID id) {
    return repository.findById(id)
        .orElseThrow(() -> new BusinessException(
            "OUTBOX_NOT_FOUND", "Outbox event not found", HttpStatus.NOT_FOUND));
  }

  private OutboxEventResponse toResponse(OutboxEventEntity e) {
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
        e.getLastError());
  }
}
