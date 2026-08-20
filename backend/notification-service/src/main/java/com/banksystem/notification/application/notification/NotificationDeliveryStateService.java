package com.banksystem.notification.application.notification;

import com.banksystem.notification.domain.notification.NotificationDeliveryEntity;
import com.banksystem.notification.domain.notification.NotificationDeliveryRepository;
import com.banksystem.notification.domain.notification.NotificationLogRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationDeliveryStateService {

  private final NotificationDeliveryRepository deliveryRepository;
  private final NotificationLogRepository notificationRepository;

  public NotificationDeliveryStateService(
      NotificationDeliveryRepository deliveryRepository,
      NotificationLogRepository notificationRepository) {
    this.deliveryRepository = deliveryRepository;
    this.notificationRepository = notificationRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<UUID> claim(Instant now, int batchSize, String worker, Instant leaseUntil) {
    List<UUID> ids = deliveryRepository.claimDueIds(now, batchSize);
    if (!ids.isEmpty()) {
      deliveryRepository.markProcessing(ids, worker, leaseUntil);
    }
    return ids;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markSent(UUID id, Instant now) {
    NotificationDeliveryEntity delivery = deliveryRepository.findById(id).orElseThrow();
    delivery.markSent(now);
    deliveryRepository.saveAndFlush(delivery);
    if (deliveryRepository.countByEventIdAndStatusNot(
        delivery.getEventId(), NotificationDeliveryEntity.STATUS_SENT) == 0) {
      notificationRepository.updateStatusByEventId(delivery.getEventId(), "SENT");
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean markFailed(
      UUID id,
      Instant now,
      Instant retryAt,
      String error,
      int maxAttempts) {
    NotificationDeliveryEntity delivery = deliveryRepository.findById(id).orElseThrow();
    int nextAttempt = delivery.getAttemptCount() + 1;
    if (nextAttempt >= maxAttempts) {
      delivery.markDead(now, error);
      notificationRepository.updateStatusByEventId(delivery.getEventId(), "DELIVERY_FAILED");
      deliveryRepository.saveAndFlush(delivery);
      return true;
    }
    delivery.markRetry(now, retryAt, error);
    deliveryRepository.saveAndFlush(delivery);
    return false;
  }
}
