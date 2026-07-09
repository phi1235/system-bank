package com.banksystem.transaction.infrastructure.outbox;

import com.banksystem.transaction.domain.OutboxEventEntity;
import com.banksystem.transaction.domain.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxService {

  private final OutboxEventRepository repository;
  private final ObjectMapper objectMapper;

  public OutboxService(OutboxEventRepository repository, ObjectMapper objectMapper) {
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public void enqueue(String eventType, UUID aggregateId, Map<String, Object> payload) {
    OutboxEventEntity e = new OutboxEventEntity();
    e.setId(UUID.randomUUID());
    e.setAggregateType("TRANSFER");
    e.setAggregateId(aggregateId);
    e.setEventType(eventType);
    e.setCreatedAt(Instant.now());
    try {
      e.setPayload(objectMapper.writeValueAsString(payload));
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Cannot serialize outbox payload", ex);
    }
    repository.save(e);
  }
}
