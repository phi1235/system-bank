package com.banksystem.notification.application.notification;

import com.banksystem.notification.domain.notification.NotificationDeliveryEntity;
import com.banksystem.notification.domain.notification.NotificationDeliveryRepository;
import com.banksystem.notification.domain.notification.NotificationLogRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationDeliveryWorker {

  private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryWorker.class);
  private static final int MAX_ERROR_LENGTH = 500;
  private static final int MAX_BACKOFF_MULTIPLIER = 64;

  private final NotificationDeliveryRepository deliveryRepository;
  private final NotificationLogRepository notificationRepository;
  private final EmailSender emailSender;
  private final SmsSender smsSender;
  private final int batchSize;
  private final int maxAttempts;
  private final Duration initialBackoff;
  private final Clock clock;

  public NotificationDeliveryWorker(
      NotificationDeliveryRepository deliveryRepository,
      NotificationLogRepository notificationRepository,
      EmailSender emailSender,
      SmsSender smsSender,
      @Value("${bank.notification.delivery-batch-size}") int batchSize,
      @Value("${bank.notification.delivery-max-attempts}") int maxAttempts,
      @Value("${bank.notification.delivery-initial-backoff}") Duration initialBackoff,
      Clock clock) {
    this.deliveryRepository = deliveryRepository;
    this.notificationRepository = notificationRepository;
    this.emailSender = emailSender;
    this.smsSender = smsSender;
    this.batchSize = batchSize;
    this.maxAttempts = maxAttempts;
    this.initialBackoff = initialBackoff;
    this.clock = clock;
  }

  @Scheduled(cron = "${bank.notification.delivery-cron}")
  @Transactional
  public void deliverDue() {
    Instant now = clock.instant();
    List<NotificationDeliveryEntity> deliveries =
        deliveryRepository.findDueForUpdate(now, batchSize);
    for (NotificationDeliveryEntity delivery : deliveries) {
      deliver(delivery, now);
    }
  }

  private void deliver(NotificationDeliveryEntity delivery, Instant now) {
    try {
      if (NotificationDeliveryEntity.CHANNEL_EMAIL.equals(delivery.getChannel())) {
        emailSender.send(delivery.getDestination(), delivery.getSubject(), delivery.getBody());
      } else if (NotificationDeliveryEntity.CHANNEL_SMS.equals(delivery.getChannel())) {
        smsSender.send(delivery.getDestination(), delivery.getSubject());
      } else {
        throw new IllegalStateException("Unsupported notification channel");
      }
      delivery.markSent(now);
      deliveryRepository.save(delivery);
      if (deliveryRepository.countByEventIdAndStatusNot(
          delivery.getEventId(), NotificationDeliveryEntity.STATUS_SENT) == 0) {
        notificationRepository.updateStatusByEventId(delivery.getEventId(), "SENT");
      }
      log.info("[NOTIFICATION-SENT] Delivered notification [{}] EventId=[{}] Channel=[{}] Dest=[{}]",
          delivery.getId(), delivery.getEventId(), delivery.getChannel(), delivery.getDestination());
    } catch (RuntimeException exception) {
      String error = sanitizeError(exception);
      int nextAttempt = delivery.getAttemptCount() + 1;
      if (nextAttempt >= maxAttempts) {
        delivery.markDead(now, error);
        notificationRepository.updateStatusByEventId(delivery.getEventId(), "DELIVERY_FAILED");
        log.error("[NOTIFICATION-DEAD] Notification delivery exhausted [{}] EventId=[{}] Channel=[{}] Error=[{}]",
            delivery.getId(), delivery.getEventId(), delivery.getChannel(), error);
      } else {
        int multiplier = Math.min(1 << Math.min(nextAttempt - 1, 6), MAX_BACKOFF_MULTIPLIER);
        delivery.markRetry(
            now, now.plus(initialBackoff.multipliedBy(multiplier)), error);
        log.warn("[NOTIFICATION-RETRY] Retry scheduled for notification [{}] EventId=[{}] Channel=[{}] Attempt={}",
            delivery.getId(), delivery.getEventId(), delivery.getChannel(), nextAttempt);
      }
      deliveryRepository.save(delivery);
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
