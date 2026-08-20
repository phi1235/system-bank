package com.banksystem.notification.application.notification;

import com.banksystem.notification.domain.notification.NotificationDeliveryEntity;
import com.banksystem.notification.domain.notification.NotificationDeliveryRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class NotificationDeliveryWorker {

  private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryWorker.class);
  private static final int MAX_ERROR_LENGTH = 500;
  private static final int MAX_BACKOFF_MULTIPLIER = 64;

  private final NotificationDeliveryRepository deliveryRepository;
  private final NotificationDeliveryStateService stateService;
  private final EmailSender emailSender;
  private final SmsSender smsSender;
  private final int batchSize;
  private final int maxAttempts;
  private final Duration initialBackoff;
  private final Clock clock;
  private final String workerId = "NOTIFICATION-WORKER-" + UUID.randomUUID();

  public NotificationDeliveryWorker(
      NotificationDeliveryRepository deliveryRepository,
      NotificationDeliveryStateService stateService,
      EmailSender emailSender,
      SmsSender smsSender,
      @Value("${bank.notification.delivery-batch-size}") int batchSize,
      @Value("${bank.notification.delivery-max-attempts}") int maxAttempts,
      @Value("${bank.notification.delivery-initial-backoff}") Duration initialBackoff,
      Clock clock) {
    this.deliveryRepository = deliveryRepository;
    this.stateService = stateService;
    this.emailSender = emailSender;
    this.smsSender = smsSender;
    this.batchSize = batchSize;
    this.maxAttempts = maxAttempts;
    this.initialBackoff = initialBackoff;
    this.clock = clock;
  }

  @Scheduled(cron = "${bank.notification.delivery-cron}")
  public void deliverDue() {
    Instant now = clock.instant();
    stateService.claim(now, batchSize, workerId, now.plusSeconds(60))
        .forEach(id -> deliver(id, clock.instant()));
  }

  private void deliver(UUID deliveryId, Instant now) {
    NotificationDeliveryEntity delivery = deliveryRepository.findById(deliveryId).orElse(null);
    if (delivery == null) {
      return;
    }
    try {
      if (NotificationDeliveryEntity.CHANNEL_EMAIL.equals(delivery.getChannel())) {
        if (delivery.getAttachmentContent() == null) {
          emailSender.send(delivery.getDestination(), delivery.getSubject(), delivery.getBody());
        } else {
          emailSender.sendWithAttachment(
              delivery.getDestination(),
              delivery.getSubject(),
              delivery.getBody(),
              delivery.getAttachmentFilename(),
              delivery.getAttachmentContent());
        }
      } else if (NotificationDeliveryEntity.CHANNEL_SMS.equals(delivery.getChannel())) {
        smsSender.send(delivery.getDestination(), delivery.getSubject());
      } else {
        throw new IllegalStateException("Unsupported notification channel");
      }
      stateService.markSent(deliveryId, now);
      log.info("[NOTIFICATION-SENT] Delivered notification [{}] EventId=[{}] Channel=[{}] Dest=[{}]",
          delivery.getId(), delivery.getEventId(), delivery.getChannel(), delivery.getDestination());
    } catch (RuntimeException exception) {
      String error = sanitizeError(exception);
      int nextAttempt = delivery.getAttemptCount() + 1;
      int multiplier = Math.min(1 << Math.min(nextAttempt - 1, 6), MAX_BACKOFF_MULTIPLIER);
      Instant retryAt = now.plus(initialBackoff.multipliedBy(multiplier));
      boolean dead = stateService.markFailed(
          deliveryId, now, retryAt, error, maxAttempts);
      if (dead) {
        log.error("[NOTIFICATION-DEAD] Notification delivery exhausted [{}] EventId=[{}] Channel=[{}] Error=[{}]",
            delivery.getId(), delivery.getEventId(), delivery.getChannel(), error);
      } else {
        log.warn("[NOTIFICATION-RETRY] Retry scheduled for notification [{}] EventId=[{}] Channel=[{}] Attempt={}",
            delivery.getId(), delivery.getEventId(), delivery.getChannel(), nextAttempt);
      }
    }
  }

  private static String sanitizeError(RuntimeException exception) {
    String message = exception.getMessage();
    String value = message == null || message.isBlank()
        ? exception.getClass().getSimpleName()
        : message.replace('\n', ' ').replace('\r', ' ');
    return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
  }
}
