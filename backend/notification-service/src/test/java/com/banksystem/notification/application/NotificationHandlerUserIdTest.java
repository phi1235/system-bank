package com.banksystem.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banksystem.notification.domain.NotificationLogEntity;
import com.banksystem.notification.domain.NotificationLogRepository;
import com.banksystem.notification.domain.ProcessedEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotificationHandlerUserIdTest {

  private ProcessedEventRepository processedEventRepository;
  private NotificationLogRepository notificationLogRepository;
  private NotificationRealtimeHub realtimeHub;
  private NotificationHandler handler;

  @BeforeEach
  void setUp() {
    processedEventRepository = mock(ProcessedEventRepository.class);
    notificationLogRepository = mock(NotificationLogRepository.class);
    realtimeHub = mock(NotificationRealtimeHub.class);
    handler = new NotificationHandler(
        processedEventRepository,
        notificationLogRepository,
        mock(MockEmailSender.class),
        mock(MockSmsSender.class),
        new ObjectMapper(),
        realtimeHub);
  }

  @Test
  void handle_persistsUserIdFromPayload() {
    UUID eventId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    when(processedEventRepository.existsById(eventId)).thenReturn(false);
    when(notificationLogRepository.save(any(NotificationLogEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(processedEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    String payload = """
        {
          "eventId":"%s",
          "eventType":"TRANSFER_COMPLETED",
          "data":{
            "userId":"%s",
            "transactionId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            "amount":1000,
            "currency":"VND",
            "description":"demo"
          }
        }
        """.formatted(eventId, userId);

    handler.handle(payload);

    ArgumentCaptor<NotificationLogEntity> cap = ArgumentCaptor.forClass(NotificationLogEntity.class);
    verify(notificationLogRepository).save(cap.capture());
    assertEquals(userId, cap.getValue().getUserId());
    assertEquals("TRANSFER_COMPLETED", cap.getValue().getTemplate());
    assertEquals(NotificationInboxService.AUDIENCE_CUSTOMER, cap.getValue().getAudience());
    verify(realtimeHub).publish(eq(userId), any());
    verify(realtimeHub, never()).publishOps(any());
  }

  @Test
  void handle_duplicateSkipped() {
    UUID eventId = UUID.fromString("33333333-3333-3333-3333-333333333333");
    when(processedEventRepository.existsById(eventId)).thenReturn(true);
    handler.handle("""
        {"eventId":"%s","eventType":"TRANSFER_COMPLETED","data":{}}
        """.formatted(eventId));
    verify(notificationLogRepository, never()).save(any());
  }

  @Test
  void handle_invalidUserIdLeavesNull() {
    UUID eventId = UUID.fromString("44444444-4444-4444-4444-444444444444");
    when(processedEventRepository.existsById(eventId)).thenReturn(false);
    when(notificationLogRepository.save(any(NotificationLogEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(processedEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    handler.handle("""
        {
          "eventId":"%s",
          "eventType":"TRANSFER_FAILED",
          "data":{"userId":"not-a-uuid","transactionId":"t1","amount":1,"currency":"VND"}
        }
        """.formatted(eventId));

    ArgumentCaptor<NotificationLogEntity> cap = ArgumentCaptor.forClass(NotificationLogEntity.class);
    verify(notificationLogRepository, times(2)).save(cap.capture());
    List<NotificationLogEntity> saved = cap.getAllValues();
    NotificationLogEntity customer = saved.stream()
        .filter(e -> NotificationInboxService.AUDIENCE_CUSTOMER.equals(e.getAudience()))
        .findFirst()
        .orElseThrow();
    NotificationLogEntity ops = saved.stream()
        .filter(e -> NotificationInboxService.AUDIENCE_OPS.equals(e.getAudience()))
        .findFirst()
        .orElseThrow();
    assertNull(customer.getUserId());
    assertEquals("TRANSFER_FAILED", customer.getTemplate());
    assertEquals("OPS_TRANSFER_FAILED", ops.getTemplate());
    assertNull(ops.getUserId());
    verify(realtimeHub).publishOps(any());
  }

  @Test
  void handle_failedTransferCreatesOpsAlert() {
    UUID eventId = UUID.fromString("55555555-5555-5555-5555-555555555555");
    UUID userId = UUID.fromString("66666666-6666-6666-6666-666666666666");
    when(processedEventRepository.existsById(eventId)).thenReturn(false);
    when(notificationLogRepository.save(any(NotificationLogEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(processedEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    handler.handle("""
        {
          "eventId":"%s",
          "eventType":"TRANSFER_FAILED",
          "failureReason":"INSUFFICIENT_FUNDS",
          "data":{
            "userId":"%s",
            "transactionId":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
            "amount":5000,
            "currency":"VND"
          }
        }
        """.formatted(eventId, userId));

    ArgumentCaptor<NotificationLogEntity> cap = ArgumentCaptor.forClass(NotificationLogEntity.class);
    verify(notificationLogRepository, times(2)).save(cap.capture());
    assertEquals(2, cap.getAllValues().size());
    verify(realtimeHub).publish(eq(userId), any());
    verify(realtimeHub).publishOps(any());
  }
}
