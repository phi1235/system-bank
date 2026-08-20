package com.banksystem.notification.application.notification;

import com.banksystem.notification.domain.notification.NotificationDeliveryEntity;
import com.banksystem.notification.domain.notification.NotificationDeliveryRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationDeliveryQueue {

  private final NotificationDeliveryRepository repository;

  public NotificationDeliveryQueue(NotificationDeliveryRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public void enqueueEmail(
      UUID eventId, String destination, String subject, String body, Instant now) {
    enqueue(eventId, NotificationDeliveryEntity.CHANNEL_EMAIL, destination, subject, body, null, null, now);
  }

  @Transactional
  public void enqueueEmailWithAttachment(
      UUID eventId,
      String destination,
      String subject,
      String body,
      String attachmentFilename,
      byte[] attachmentContent,
      Instant now) {
    enqueue(
        eventId,
        NotificationDeliveryEntity.CHANNEL_EMAIL,
        destination,
        subject,
        body,
        attachmentFilename,
        attachmentContent,
        now);
  }

  @Transactional
  public void enqueueSms(
      UUID eventId, String destination, String subject, String body, Instant now) {
    enqueue(eventId, NotificationDeliveryEntity.CHANNEL_SMS, destination, subject, body, null, null, now);
  }

  private void enqueue(
      UUID eventId,
      String channel,
      String destination,
      String subject,
      String body,
      String attachmentFilename,
      byte[] attachmentContent,
      Instant now) {
    UUID deliveryId = UUID.nameUUIDFromBytes(
        (eventId + ":" + channel).getBytes(StandardCharsets.UTF_8));
    repository.enqueueIdempotently(
        deliveryId,
        eventId,
        channel,
        destination,
        subject,
        body,
        attachmentFilename,
        attachmentContent,
        now);
  }
}
