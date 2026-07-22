package com.banksystem.customer.application;

import com.banksystem.customer.domain.CustomerEntity;
import com.banksystem.customer.infrastructure.feign.NotificationClient;
import com.banksystem.customer.infrastructure.feign.NotificationClientDtos.CreateOpsAlertRequest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Best-effort OPS alerts for KYC changes; never blocks KYC update. */
@Service
public class OpsAlertPublisher {

  private static final Logger log = LoggerFactory.getLogger(OpsAlertPublisher.class);

  public static final String TEMPLATE_KYC_UPDATED = "OPS_KYC_UPDATED";

  private final NotificationClient notificationClient;
  private final String apiKey;

  public OpsAlertPublisher(
      NotificationClient notificationClient,
      @Value("${bank.internal.notification-api-key}") String apiKey) {
    this.notificationClient = notificationClient;
    this.apiKey = apiKey;
  }

  public void kycUpdated(CustomerEntity customer, String previousStatus) {
    UUID eventId = UUID.nameUUIDFromBytes(
        ("ops-kyc:" + customer.getId() + ":" + customer.getKycStatus() + ":" + customer.getUpdatedAt())
            .getBytes(StandardCharsets.UTF_8));
    String body = "KYC status changed"
        + " customerId=" + customer.getId()
        + " from=" + (previousStatus == null ? "n/a" : previousStatus)
        + " to=" + customer.getKycStatus()
        + " name=" + nullToNa(customer.getFullName());
    publishQuietly(eventId, TEMPLATE_KYC_UPDATED, body);
  }

  private void publishQuietly(UUID eventId, String template, String body) {
    try {
      notificationClient.createOpsAlert(
          new CreateOpsAlertRequest(eventId.toString(), template, body),
          apiKey);
    } catch (Exception ex) {
      log.warn("Failed to publish OPS alert template={}: {}", template, ex.getMessage());
    }
  }

  private static String nullToNa(String v) {
    return v == null || v.isBlank() ? "n/a" : v;
  }
}
