package com.banksystem.notification.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banksystem.notification.domain.NotificationLogRepository;
import com.banksystem.notification.domain.ProcessedEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationHandlerIdempotencyTest {

  @Test
  void skipsAlreadyProcessedEvent() {
    ProcessedEventRepository processed = mock(ProcessedEventRepository.class);
    NotificationLogRepository logs = mock(NotificationLogRepository.class);
    UUID eventId = UUID.randomUUID();
    when(processed.existsById(eventId)).thenReturn(true);

    NotificationHandler handler = new NotificationHandler(
        processed,
        logs,
        mock(MockEmailSender.class),
        mock(MockSmsSender.class),
        new ObjectMapper(),
        mock(NotificationRealtimeHub.class));

    String payload = "{\"eventId\":\"" + eventId + "\",\"eventType\":\"TRANSACTION_COMPLETED\",\"data\":{}}";
    assertDoesNotThrow(() -> handler.handle(payload));
    verify(logs, never()).save(any());
  }
}
