package com.banksystem.notification.application.notification.impl;
import com.banksystem.notification.application.notification.*;
import com.banksystem.notification.domain.notification.*;
import com.banksystem.notification.domain.event.*;
import com.banksystem.notification.api.dto.*;

import com.banksystem.notification.api.dto.NotificationDtos.NotificationItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * In-process fan-out for notification SSE streams.
 * User channel = customer IB inbox; ops channel = shared staff alerts.
 * Single-instance only; multi-replica would need Redis pub/sub later.
 */
@Component
public class NotificationRealtimeHub {

  private static final Logger log = LoggerFactory.getLogger(NotificationRealtimeHub.class);
  private static final long TIMEOUT_MS = 0L; // no timeout; client reconnects on drop

  private final ObjectMapper objectMapper;
  private final Map<UUID, CopyOnWriteArrayList<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();
  private final CopyOnWriteArrayList<SseEmitter> opsEmitters = new CopyOnWriteArrayList<>();

  public NotificationRealtimeHub(ObjectMapper objectMapper) {
    // Spring Boot ObjectMapper already has JavaTimeModule; copy + register keeps unit tests safe too.
    this.objectMapper = objectMapper.copy().findAndRegisterModules();
  }

  public SseEmitter subscribe(UUID userId) {
    SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
    emittersByUser.computeIfAbsent(userId, id -> new CopyOnWriteArrayList<>()).add(emitter);

    Runnable cleanup = () -> remove(userId, emitter);
    emitter.onCompletion(cleanup);
    emitter.onTimeout(cleanup);
    emitter.onError(ex -> cleanup.run());

    try {
      emitter.send(SseEmitter.event().name("connected").data(Map.of("ok", true, "channel", "user")));
    } catch (IOException ex) {
      cleanup.run();
    }
    return emitter;
  }

  public SseEmitter subscribeOps() {
    SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
    opsEmitters.add(emitter);

    Runnable cleanup = () -> removeOps(emitter);
    emitter.onCompletion(cleanup);
    emitter.onTimeout(cleanup);
    emitter.onError(ex -> cleanup.run());

    try {
      emitter.send(SseEmitter.event().name("connected").data(Map.of("ok", true, "channel", "ops")));
    } catch (IOException ex) {
      cleanup.run();
    }
    return emitter;
  }

  public void publish(UUID userId, NotificationItem item) {
    if (userId == null || item == null) {
      return;
    }
    List<SseEmitter> emitters = emittersByUser.get(userId);
    sendAll(emitters, item, "userId=" + userId);
  }

  public void publishOps(NotificationItem item) {
    if (item == null) {
      return;
    }
    sendAll(opsEmitters, item, "ops");
  }

  private void sendAll(List<SseEmitter> emitters, NotificationItem item, String label) {
    if (emitters == null || emitters.isEmpty()) {
      return;
    }
    String json;
    try {
      json = objectMapper.writeValueAsString(item);
    } catch (Exception ex) {
      log.warn("Failed to serialize notification for SSE {}: {}", label, ex.getMessage());
      return;
    }
    for (SseEmitter emitter : emitters) {
      try {
        emitter.send(SseEmitter.event().name("notification").data(json));
      } catch (Exception ex) {
        if (label.startsWith("userId=")) {
          UUID userId = UUID.fromString(label.substring("userId=".length()));
          remove(userId, emitter);
        } else {
          removeOps(emitter);
        }
        try {
          emitter.completeWithError(ex);
        } catch (Exception ignored) {
          // already closed
        }
      }
    }
  }

  private void remove(UUID userId, SseEmitter emitter) {
    CopyOnWriteArrayList<SseEmitter> list = emittersByUser.get(userId);
    if (list == null) {
      return;
    }
    list.remove(emitter);
    if (list.isEmpty()) {
      emittersByUser.remove(userId, list);
    }
  }

  private void removeOps(SseEmitter emitter) {
    opsEmitters.remove(emitter);
  }
}
