package com.banksystem.notification.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.banksystem.notification.api.dto.NotificationDtos.NotificationItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class NotificationRealtimeHubTest {

  @Test
  void subscribeAndPublishDoesNotThrow() {
    NotificationRealtimeHub hub = new NotificationRealtimeHub(new ObjectMapper());
    UUID userId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    SseEmitter emitter = hub.subscribe(userId);
    assertNotNull(emitter);

    NotificationItem item = new NotificationItem(
        UUID.randomUUID().toString(),
        "EMAIL",
        "TRANSFER_COMPLETED",
        "SENT",
        "ok",
        false,
        null,
        Instant.parse("2026-07-21T10:00:00Z"));

    assertDoesNotThrow(() -> hub.publish(userId, item));
    assertDoesNotThrow(() -> hub.publish(null, item));
    assertDoesNotThrow(() -> hub.publish(userId, null));
  }

  @Test
  void opsSubscribeAndPublishDoesNotThrow() {
    NotificationRealtimeHub hub = new NotificationRealtimeHub(new ObjectMapper());
    SseEmitter emitter = hub.subscribeOps();
    assertNotNull(emitter);

    NotificationItem item = new NotificationItem(
        UUID.randomUUID().toString(),
        "OPS",
        "OPS_TRANSFER_FAILED",
        "OPEN",
        "transfer failed",
        false,
        null,
        Instant.parse("2026-07-21T10:00:00Z"));

    assertDoesNotThrow(() -> hub.publishOps(item));
    assertDoesNotThrow(() -> hub.publishOps(null));
  }
}
