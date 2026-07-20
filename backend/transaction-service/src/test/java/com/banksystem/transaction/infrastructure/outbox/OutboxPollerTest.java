package com.banksystem.transaction.infrastructure.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banksystem.transaction.domain.OutboxEventEntity;
import com.banksystem.transaction.domain.OutboxEventRepository;
import com.banksystem.transaction.domain.OutboxStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class OutboxPollerTest {

  private static final Instant NOW = Instant.parse("2026-07-21T02:00:00Z");

  private OutboxEventRepository repository;
  private KafkaTemplate<String, String> kafkaTemplate;
  private OutboxRetryPolicy retryPolicy;
  private OutboxMetrics metrics;
  private OutboxPoller poller;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    repository = mock(OutboxEventRepository.class);
    kafkaTemplate = mock(KafkaTemplate.class);
    metrics = mock(OutboxMetrics.class);
    retryPolicy = new OutboxRetryPolicy(3, 1000, 60_000, Clock.fixed(NOW, ZoneOffset.UTC));
    poller = new OutboxPoller(
        repository,
        kafkaTemplate,
        retryPolicy,
        metrics,
        50,
        "tx.completed",
        "tx.failed");
  }

  @Test
  @SuppressWarnings("unchecked")
  void marksPublishedOnSuccess() {
    OutboxEventEntity event = pendingEvent("TRANSACTION_COMPLETED");
    CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
    when(kafkaTemplate.send(eq("tx.completed"), anyString(), anyString())).thenReturn(future);

    poller.publishOne(event);

    assertEquals(OutboxStatus.PUBLISHED.name(), event.getStatus());
    assertEquals(NOW, event.getPublishedAt());
    assertNull(event.getLastError());
    verify(repository).save(event);
    verify(metrics).incrementPublished();
  }

  @Test
  @SuppressWarnings("unchecked")
  void schedulesRetryOnTransientFailure() {
    OutboxEventEntity event = pendingEvent("TRANSACTION_COMPLETED");
    CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
    future.completeExceptionally(new RuntimeException("broker down"));
    when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);

    poller.publishOne(event);

    assertEquals(OutboxStatus.PENDING.name(), event.getStatus());
    assertEquals(1, event.getAttemptCount());
    assertEquals(NOW.plusSeconds(1), event.getNextAttemptAt());
    assertEquals("broker down", event.getLastError());
    assertNull(event.getPublishedAt());
    verify(repository).save(event);
    verify(metrics).incrementRetry();
  }

  @Test
  @SuppressWarnings("unchecked")
  void marksDeadAfterMaxAttempts() {
    OutboxEventEntity event = pendingEvent("TRANSACTION_FAILED");
    event.setAttemptCount(2); // next failure => 3 == max
    CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
    future.completeExceptionally(new RuntimeException("still down"));
    when(kafkaTemplate.send(eq("tx.failed"), anyString(), anyString())).thenReturn(future);

    poller.publishOne(event);

    assertEquals(OutboxStatus.DEAD.name(), event.getStatus());
    assertEquals(3, event.getAttemptCount());
    assertEquals("still down", event.getLastError());
    assertNull(event.getPublishedAt());
    verify(repository).save(event);
    verify(metrics).incrementDead();
  }

  @Test
  void resolveUsesFailedTopic() {
    OutboxEventEntity event = pendingEvent("TRANSACTION_FAILED");
    CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
    when(kafkaTemplate.send(eq("tx.failed"), anyString(), anyString())).thenReturn(future);

    poller.publishOne(event);

    verify(kafkaTemplate).send(eq("tx.failed"), eq(event.getAggregateId().toString()), eq(event.getPayload()));
    verify(kafkaTemplate, never()).send(eq("tx.completed"), anyString(), anyString());
  }

  private OutboxEventEntity pendingEvent(String type) {
    OutboxEventEntity e = new OutboxEventEntity();
    e.setId(UUID.randomUUID());
    e.setAggregateType("TRANSFER");
    e.setAggregateId(UUID.randomUUID());
    e.setEventType(type);
    e.setPayload("{\"ok\":true}");
    e.setCreatedAt(NOW);
    e.setStatus(OutboxStatus.PENDING.name());
    e.setAttemptCount(0);
    e.setNextAttemptAt(NOW);
    return e;
  }
}
