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
  public static final String TEMPLATE_SUPPORT_OPENED = "OPS_SUPPORT_TICKET_OPENED";
  public static final String TEMPLATE_SUPPORT_RESOLVED = "OPS_SUPPORT_TICKET_RESOLVED";
  public static final String TEMPLATE_SUPPORT_REJECTED = "OPS_SUPPORT_TICKET_REJECTED";

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

  public void supportTicketOpened(com.banksystem.customer.domain.SupportTicketEntity ticket) {
    UUID eventId = UUID.nameUUIDFromBytes(
        ("ops-support-open:" + ticket.getId()).getBytes(StandardCharsets.UTF_8));
    String body = "Support ticket opened"
        + " ticketId=" + ticket.getId()
        + " userId=" + ticket.getUserId()
        + " category=" + ticket.getCategory()
        + " priority=" + ticket.getPriority()
        + " subject=" + nullToNa(ticket.getSubject());
    publishQuietly(eventId, TEMPLATE_SUPPORT_OPENED, body);
  }

  public void supportTicketResolved(com.banksystem.customer.domain.SupportTicketEntity ticket) {
    UUID eventId = UUID.nameUUIDFromBytes(
        ("ops-support-resolved:" + ticket.getId() + ":" + ticket.getUpdatedAt())
            .getBytes(StandardCharsets.UTF_8));
    String body = "Support ticket resolved"
        + " ticketId=" + ticket.getId()
        + " userId=" + ticket.getUserId()
        + " by=" + (ticket.getResolvedBy() == null ? "n/a" : ticket.getResolvedBy());
    publishQuietly(eventId, TEMPLATE_SUPPORT_RESOLVED, body);
  }

  public void supportTicketRejected(com.banksystem.customer.domain.SupportTicketEntity ticket) {
    UUID eventId = UUID.nameUUIDFromBytes(
        ("ops-support-rejected:" + ticket.getId() + ":" + ticket.getUpdatedAt())
            .getBytes(StandardCharsets.UTF_8));
    String body = "Support ticket rejected"
        + " ticketId=" + ticket.getId()
        + " userId=" + ticket.getUserId()
        + " reason=" + nullToNa(ticket.getRejectReason());
    publishQuietly(eventId, TEMPLATE_SUPPORT_REJECTED, body);
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
