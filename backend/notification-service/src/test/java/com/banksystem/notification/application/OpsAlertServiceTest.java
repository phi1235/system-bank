package com.banksystem.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.notification.api.dto.NotificationDtos.NotificationItem;
import com.banksystem.notification.api.dto.OpsAlertDtos.CreateOpsAlertRequest;
import com.banksystem.notification.domain.NotificationLogEntity;
import com.banksystem.notification.domain.NotificationLogRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OpsAlertServiceTest {

  private NotificationLogRepository repository;
  private NotificationRealtimeHub realtimeHub;
  private OpsAlertService service;

  @BeforeEach
  void setUp() {
    repository = mock(NotificationLogRepository.class);
    realtimeHub = mock(NotificationRealtimeHub.class);
    service = new OpsAlertService(repository, realtimeHub);
  }

  @Test
  void create_persistsOpsAudienceAndPublishes() {
    when(repository.findByEventId(any())).thenReturn(Optional.empty());
    when(repository.save(any(NotificationLogEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    UUID eventId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    NotificationItem item = service.create(
        new CreateOpsAlertRequest(eventId.toString(), "OPS_OUTBOX_DEAD", "outbox dead"));

    ArgumentCaptor<NotificationLogEntity> cap = ArgumentCaptor.forClass(NotificationLogEntity.class);
    verify(repository).save(cap.capture());
    NotificationLogEntity saved = cap.getValue();
    assertEquals(eventId, saved.getEventId());
    assertEquals("OPS", saved.getChannel());
    assertEquals(NotificationInboxService.AUDIENCE_OPS, saved.getAudience());
    assertEquals("OPS_OUTBOX_DEAD", saved.getTemplate());
    assertEquals("OPEN", saved.getStatus());
    assertEquals("outbox dead", item.body());
    verify(realtimeHub).publishOps(any());
  }

  @Test
  void create_isIdempotentOnExistingEventId() {
    UUID eventId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    NotificationLogEntity existing = new NotificationLogEntity();
    existing.setId(UUID.randomUUID());
    existing.setEventId(eventId);
    existing.setChannel("OPS");
    existing.setRecipient("ops@bank.local");
    existing.setTemplate("OPS_OUTBOX_DEAD");
    existing.setStatus("OPEN");
    existing.setBody("already");
    existing.setAudience(NotificationInboxService.AUDIENCE_OPS);
    existing.setCreatedAt(Instant.now());
    when(repository.findByEventId(eventId)).thenReturn(Optional.of(existing));

    NotificationItem item = service.create(
        new CreateOpsAlertRequest(eventId.toString(), "OPS_OUTBOX_DEAD", "again"));

    assertEquals("already", item.body());
    verify(repository, never()).save(any());
    verify(realtimeHub, never()).publishOps(any());
  }

  @Test
  void create_rejectsInvalidEventId() {
    BusinessException ex = assertThrows(
        BusinessException.class,
        () -> service.create(new CreateOpsAlertRequest("not-uuid", "OPS_X", "body")));
    assertEquals("INVALID_EVENT_ID", ex.getCode());
  }
}
