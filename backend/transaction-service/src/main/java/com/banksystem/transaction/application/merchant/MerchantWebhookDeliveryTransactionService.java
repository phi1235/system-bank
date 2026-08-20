package com.banksystem.transaction.application.merchant;

import com.banksystem.transaction.domain.merchant.MerchantWebhookDeliveryEntity;
import com.banksystem.transaction.domain.merchant.MerchantWebhookDeliveryRepository;
import com.banksystem.transaction.domain.merchant.MerchantWebhookDeliveryStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MerchantWebhookDeliveryTransactionService {

  private static final Logger log = LoggerFactory.getLogger(MerchantWebhookDeliveryTransactionService.class);

  private final MerchantWebhookDeliveryRepository deliveryRepository;

  public MerchantWebhookDeliveryTransactionService(
      MerchantWebhookDeliveryRepository deliveryRepository) {
    this.deliveryRepository = deliveryRepository;
  }

  public record DeliveryClaimContext(
      UUID deliveryId,
      UUID endpointId,
      UUID organizationId,
      UUID eventId,
      String eventType,
      String payload,
      int retryCount,
      UUID claimToken
  ) {}

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<DeliveryClaimContext> claimDeliveriesBatch(int limit, int leaseSeconds) {
    Instant now = Instant.now();
    List<MerchantWebhookDeliveryEntity> claimedList = deliveryRepository.claimPendingDeliveries(now, limit);
    if (claimedList.isEmpty()) {
      return List.of();
    }

    List<DeliveryClaimContext> contexts = new ArrayList<>();
    for (MerchantWebhookDeliveryEntity entity : claimedList) {
      UUID token = UUID.randomUUID();
      entity.setStatus(MerchantWebhookDeliveryStatus.SENDING);
      entity.setClaimToken(token);
      entity.setClaimedAt(now);
      entity.setClaimExpiresAt(now.plusSeconds(leaseSeconds));
      entity.setUpdatedAt(now);
      deliveryRepository.save(entity);

      contexts.add(new DeliveryClaimContext(
          entity.getId(),
          entity.getEndpointId(),
          entity.getOrganizationId(),
          entity.getEventId(),
          entity.getEventType(),
          entity.getPayload(),
          entity.getRetryCount(),
          token
      ));
    }

    log.info("[WEBHOOK-DELIVERY-CLAIM] Claimed {} pending deliveries for sending", contexts.size());
    return contexts;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean markSuccess(UUID deliveryId, UUID claimToken, int statusCode, String responseBody) {
    Instant now = Instant.now();
    Optional<MerchantWebhookDeliveryEntity> opt = deliveryRepository.findByIdForUpdate(deliveryId);
    if (opt.isEmpty()) {
      return false;
    }

    MerchantWebhookDeliveryEntity delivery = opt.get();
    if (!ownsClaim(delivery, claimToken)) {
      log.warn("[WEBHOOK-DELIVERY-TX] Ignoring stale success for delivery [{}]", deliveryId);
      return false;
    }
    delivery.setStatus(MerchantWebhookDeliveryStatus.SUCCESS);
    delivery.setResponseStatusCode(statusCode);
    delivery.setResponseBody(responseBody != null && responseBody.length() > 1000 ? responseBody.substring(0, 1000) : responseBody);
    delivery.setErrorMessage(null);
    delivery.setClaimToken(null);
    delivery.setClaimedAt(null);
    delivery.setClaimExpiresAt(null);
    delivery.setUpdatedAt(now);
    deliveryRepository.save(delivery);
    log.info("[WEBHOOK-DELIVERY-TX] Delivery [{}] marked SUCCESS (status={})", deliveryId, statusCode);
    return true;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean markRetryOrDeadLetter(
      UUID deliveryId, UUID claimToken, Integer statusCode, String responseBody, String error) {
    Instant now = Instant.now();
    Optional<MerchantWebhookDeliveryEntity> opt = deliveryRepository.findByIdForUpdate(deliveryId);
    if (opt.isEmpty()) {
      return false;
    }

    MerchantWebhookDeliveryEntity delivery = opt.get();
    if (!ownsClaim(delivery, claimToken)) {
      log.warn("[WEBHOOK-DELIVERY-TX] Ignoring stale failure for delivery [{}]", deliveryId);
      return false;
    }
    int nextCount = delivery.getRetryCount() + 1;
    delivery.setRetryCount(nextCount);
    delivery.setResponseStatusCode(statusCode);
    delivery.setResponseBody(responseBody != null && responseBody.length() > 1000 ? responseBody.substring(0, 1000) : responseBody);
    delivery.setErrorMessage(error != null && error.length() > 500 ? error.substring(0, 500) : error);
    delivery.setClaimToken(null);
    delivery.setClaimedAt(null);
    delivery.setClaimExpiresAt(null);
    delivery.setUpdatedAt(now);

    if (nextCount >= 5) {
      delivery.setStatus(MerchantWebhookDeliveryStatus.DEAD_LETTER);
      log.error("[WEBHOOK-DELIVERY-TX] Delivery [{}] exceeded max retries (5), marked DEAD_LETTER: {}", deliveryId, error);
    } else {
      delivery.setStatus(MerchantWebhookDeliveryStatus.RETRYING);
      long backoffSeconds = 30L * (1L << Math.min(nextCount, 6));
      delivery.setNextRetryAt(now.plusSeconds(backoffSeconds));
      log.warn("[WEBHOOK-DELIVERY-TX] Delivery [{}] marked RETRYING (retry={}, nextRetryAt={}): {}",
          deliveryId, nextCount, delivery.getNextRetryAt(), error);
    }

    deliveryRepository.save(delivery);
    return true;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean markDeadLetterEndpointInactive(UUID deliveryId, UUID claimToken, String reason) {
    Instant now = Instant.now();
    Optional<MerchantWebhookDeliveryEntity> opt = deliveryRepository.findByIdForUpdate(deliveryId);
    if (opt.isEmpty()) {
      return false;
    }

    MerchantWebhookDeliveryEntity delivery = opt.get();
    if (!ownsClaim(delivery, claimToken)) {
      log.warn("[WEBHOOK-DELIVERY-TX] Ignoring stale endpoint failure for delivery [{}]", deliveryId);
      return false;
    }
    delivery.setStatus(MerchantWebhookDeliveryStatus.DEAD_LETTER);
    delivery.setErrorMessage(reason);
    delivery.setClaimToken(null);
    delivery.setClaimedAt(null);
    delivery.setClaimExpiresAt(null);
    delivery.setUpdatedAt(now);
    deliveryRepository.save(delivery);
    return true;
  }

  private boolean ownsClaim(MerchantWebhookDeliveryEntity delivery, UUID claimToken) {
    return claimToken != null
        && delivery.getStatus() == MerchantWebhookDeliveryStatus.SENDING
        && Objects.equals(delivery.getClaimToken(), claimToken);
  }
}
