package com.banksystem.transaction.application.merchant;

import com.banksystem.transaction.domain.merchant.MerchantWebhookDeliveryEntity;
import com.banksystem.transaction.domain.merchant.MerchantWebhookDeliveryRepository;
import com.banksystem.transaction.domain.merchant.MerchantWebhookEndpointEntity;
import com.banksystem.transaction.domain.merchant.MerchantWebhookEndpointRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MerchantWebhookDispatcher {

  private static final Logger log = LoggerFactory.getLogger(MerchantWebhookDispatcher.class);

  private final MerchantWebhookEndpointRepository endpointRepository;
  private final MerchantWebhookDeliveryRepository deliveryRepository;
  private final ObjectMapper objectMapper;

  public MerchantWebhookDispatcher(
      MerchantWebhookEndpointRepository endpointRepository,
      MerchantWebhookDeliveryRepository deliveryRepository,
      ObjectMapper objectMapper) {
    this.endpointRepository = endpointRepository;
    this.deliveryRepository = deliveryRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public void dispatchEvent(UUID organizationId, UUID eventId, String eventType, Map<String, Object> payloadMap) {
    List<MerchantWebhookEndpointEntity> endpoints = endpointRepository.findByOrganizationId(organizationId);
    if (endpoints == null || endpoints.isEmpty()) {
      return;
    }

    Instant now = Instant.now();
    String payloadJson;
    try {
      payloadJson = objectMapper.writeValueAsString(payloadMap);
    } catch (Exception e) {
      log.error("[MERCHANT-WEBHOOK-DISPATCH] Failed to serialize webhook payload for event {}: {}", eventId, e.getMessage());
      return;
    }

    for (MerchantWebhookEndpointEntity endpoint : endpoints) {
      if (!"ACTIVE".equalsIgnoreCase(endpoint.getStatus())) {
        continue;
      }

      String subscribed = endpoint.getEventTypes();
      if (subscribed != null && !subscribed.contains("*") && !subscribed.contains(eventType)) {
        continue;
      }

      if (deliveryRepository.existsByEndpointIdAndEventId(endpoint.getId(), eventId)) {
        continue;
      }

      MerchantWebhookDeliveryEntity delivery = MerchantWebhookDeliveryEntity.create(
          endpoint.getId(), organizationId, eventId, eventType, payloadJson, now
      );
      deliveryRepository.save(delivery);
      log.info("[MERCHANT-WEBHOOK-DISPATCH] Created delivery {} for endpoint {} event {}",
          delivery.getId(), endpoint.getId(), eventId);
    }
  }
}
