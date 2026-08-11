package com.banksystem.transaction.infrastructure.outbox;

import com.banksystem.transaction.domain.outbox.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
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
    String dedupeKey = "TRANSFER:" + aggregateId + ":" + eventType;
    Instant now = clock.instant();
    String serialized;
    try {
      serialized = objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Cannot serialize outbox payload", ex);
    }
    UUID eventId = UUID.nameUUIDFromBytes(dedupeKey.getBytes(StandardCharsets.UTF_8));
    repository.insertIfAbsent(eventId, aggregateId, eventType, dedupeKey, serialized, now);
  }
}
