package com.banksystem.transaction.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banksystem.common.api.PageResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.api.dto.OutboxDtos.OutboxEventResponse;
import com.banksystem.transaction.application.query.OutboxListQuery;
import com.banksystem.transaction.domain.OutboxEventEntity;
import com.banksystem.transaction.domain.OutboxEventRepository;
import com.banksystem.transaction.domain.OutboxStatus;
import com.banksystem.transaction.infrastructure.outbox.OutboxMetrics;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class OutboxAdminServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-21T03:00:00Z");

  private OutboxEventRepository repository;
  private OutboxMetrics metrics;
  private OutboxAdminService service;

  @BeforeEach
  void setUp() {
    repository = mock(OutboxEventRepository.class);
    metrics = mock(OutboxMetrics.class);
    service = new OutboxAdminService(
        repository,
        metrics,
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void list_defaultsToDeadAndMapsPage() {
    OutboxEventEntity dead = deadEvent();
    when(repository.findByStatusOrderByCreatedAtDesc(eq("DEAD"), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(dead), PageRequest.of(0, 20), 1));

    PageResponse<OutboxEventResponse> page = service.list(OutboxListQuery.of(null, 0, 20));

    assertEquals(1, page.items().size());
    assertEquals(dead.getId().toString(), page.items().get(0).id());
    assertEquals("DEAD", page.items().get(0).status());
  }

  @Test
  void replay_resetsDeadToPending() {
    OutboxEventEntity dead = deadEvent();
    when(repository.findById(dead.getId())).thenReturn(Optional.of(dead));
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    OutboxEventResponse res = service.replay(dead.getId());

    assertEquals("PENDING", res.status());
    assertEquals(0, res.attemptCount());
    assertEquals(NOW, res.nextAttemptAt());
    assertNull(res.lastError());
    assertNull(res.publishedAt());
    verify(metrics).incrementReplayed();
  }

  @Test
  void replay_rejectsNonDead() {
    OutboxEventEntity pending = deadEvent();
    pending.setStatus(OutboxStatus.PENDING.name());
    when(repository.findById(pending.getId())).thenReturn(Optional.of(pending));

    BusinessException ex = assertThrows(BusinessException.class, () -> service.replay(pending.getId()));
    assertEquals("OUTBOX_NOT_DEAD", ex.getCode());
  }

  @Test
  void replay_notFound() {
    UUID id = UUID.randomUUID();
    when(repository.findById(id)).thenReturn(Optional.empty());
    BusinessException ex = assertThrows(BusinessException.class, () -> service.replay(id));
    assertEquals("OUTBOX_NOT_FOUND", ex.getCode());
  }

  private OutboxEventEntity deadEvent() {
    OutboxEventEntity e = new OutboxEventEntity();
    e.setId(UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"));
    e.setAggregateType("TRANSFER");
    e.setAggregateId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    e.setEventType("TRANSACTION_COMPLETED");
    e.setPayload("{}");
    e.setCreatedAt(NOW.minusSeconds(60));
    e.setStatus(OutboxStatus.DEAD.name());
    e.setAttemptCount(10);
    e.setNextAttemptAt(NOW.minusSeconds(10));
    e.setLastError("broker down");
    return e;
  }
}
