package com.banksystem.notification.application.notification;

import com.banksystem.notification.domain.notification.NotificationDeliveryEntity;
import com.banksystem.notification.domain.notification.NotificationDeliveryRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class NotificationDeliveryQueue {

  private final NotificationDeliveryRepository repository;

  public NotificationDeliveryQueue(NotificationDeliveryRepository repository) {
    this.repository = repository;
  }

  public void enqueueEmail(
      UUID eventId, String destination, String subject, String body, Instant now) {
    enqueue(eventId, NotificationDeliveryEntity.CHANNEL_EMAIL, destination, subject, body, now);
  }

  public void enqueueSms(
      UUID eventId, String destination, String subject, String body, Instant now) {
    enqueue(eventId, NotificationDeliveryEntity.CHANNEL_SMS, destination, subject, body, now);
  }

  private void enqueue(
      UUID eventId,
      String channel,
      String destination,
      String subject,
      String body,
      Instant now) {
    UUID deliveryId = UUID.nameUUIDFromBytes(
        (eventId + ":" + channel).getBytes(StandardCharsets.UTF_8));
    repository.save(NotificationDeliveryEntity.pending(
        deliveryId, eventId, channel, destination, subject, body, now));
  }
}
