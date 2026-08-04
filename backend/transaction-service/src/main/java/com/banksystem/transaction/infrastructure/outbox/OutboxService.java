package com.banksystem.transaction.infrastructure.outbox;

import com.banksystem.transaction.domain.outbox.OutboxEventEntity;
import com.banksystem.transaction.domain.outbox.OutboxEventRepository;
import com.banksystem.transaction.domain.outbox.OutboxStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxService {

  private final OutboxEventRepository repository;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public OutboxService(OutboxEventRepository repository, ObjectMapper objectMapper, Clock clock) {
    this.repository = repository;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  @Transactional
  public void enqueue(String eventType, UUID aggregateId, Map<String, Object> payload) {
    Instant now = clock.instant();
    OutboxEventEntity e = new OutboxEventEntity();
    e.setId(UUID.randomUUID());
    e.setAggregateType("TRANSFER");
    e.setAggregateId(aggregateId);
    e.setEventType(eventType);
    e.setCreatedAt(now);
    e.setStatus(OutboxStatus.PENDING.name());
    e.setAttemptCount(0);
    e.setNextAttemptAt(now);
    try {
      e.setPayload(objectMapper.writeValueAsString(payload));
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Cannot serialize outbox payload", ex);
    }
    repository.save(e);
  }
}
