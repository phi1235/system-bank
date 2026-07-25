package com.banksystem.customer.application;

import com.banksystem.customer.domain.CustomerEntity;
import com.banksystem.customer.domain.SupportTicketEntity;
import com.banksystem.customer.infrastructure.feign.NotificationClient;
import com.banksystem.customer.infrastructure.feign.NotificationClientDtos.CreateOpsAlertRequest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Best-effort OPS alerts for KYC / support; never blocks primary flows.
 * Alert body is human-readable for staff (no raw ticketId=/userId= dumps).
 */
@Service
public class OpsAlertPublisher {

  private static final Logger log = LoggerFactory.getLogger(OpsAlertPublisher.class);

  public static final String TEMPLATE_KYC_UPDATED = "OPS_KYC_UPDATED";
  public static final String TEMPLATE_SUPPORT_OPENED = "OPS_SUPPORT_TICKET_OPENED";
  public static final String TEMPLATE_SUPPORT_RESOLVED = "OPS_SUPPORT_TICKET_RESOLVED";
  public static final String TEMPLATE_SUPPORT_REJECTED = "OPS_SUPPORT_TICKET_REJECTED";
  public static final String TEMPLATE_SUPPORT_CUSTOMER_REPLY = "OPS_SUPPORT_TICKET_CUSTOMER_REPLY";

  public static final String ACTION_SUPPORT_TICKET = "SUPPORT_TICKET";
  public static final String ACTION_KYC = "KYC";

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
    String name = displayName(customer.getFullName());
    String from = previousStatus == null || previousStatus.isBlank() ? "—" : previousStatus;
    String to = customer.getKycStatus() == null ? "—" : customer.getKycStatus();
    String body = "KYC của " + name + " đổi từ " + from + " → " + to + ".";
    publishQuietly(
        eventId,
        TEMPLATE_KYC_UPDATED,
        body,
        ACTION_KYC,
        customer.getId().toString(),
        "/admin/customers?q=" + customer.getId());
  }

  public void supportTicketOpened(SupportTicketEntity ticket) {
    UUID eventId = UUID.nameUUIDFromBytes(
        ("ops-support-open:" + ticket.getId()).getBytes(StandardCharsets.UTF_8));
    String body =
        "Ticket mới: \""
            + displaySubject(ticket)
            + "\" ("
            + nullToDash(ticket.getCategory())
            + " · "
            + nullToDash(ticket.getPriority())
            + ").";
    publishTicket(eventId, TEMPLATE_SUPPORT_OPENED, body, ticket.getId());
  }

  public void supportTicketResolved(SupportTicketEntity ticket) {
    UUID eventId = UUID.nameUUIDFromBytes(
        ("ops-support-resolved:" + ticket.getId() + ":" + ticket.getUpdatedAt())
            .getBytes(StandardCharsets.UTF_8));
    String body = "Ticket \"" + displaySubject(ticket) + "\" đã được giải quyết.";
    publishTicket(eventId, TEMPLATE_SUPPORT_RESOLVED, body, ticket.getId());
  }

  public void supportTicketRejected(SupportTicketEntity ticket) {
    UUID eventId = UUID.nameUUIDFromBytes(
        ("ops-support-rejected:" + ticket.getId() + ":" + ticket.getUpdatedAt())
            .getBytes(StandardCharsets.UTF_8));
    String reason = ticket.getRejectReason();
    String body =
        "Ticket \""
            + displaySubject(ticket)
            + "\" bị từ chối."
            + (reason == null || reason.isBlank() ? "" : " Lý do: " + preview(reason));
    publishTicket(eventId, TEMPLATE_SUPPORT_REJECTED, body, ticket.getId());
  }

  public void supportTicketCustomerReply(SupportTicketEntity ticket, String message) {
    UUID eventId = UUID.nameUUIDFromBytes(
        ("ops-support-customer-reply:" + ticket.getId() + ":" + ticket.getUpdatedAt() + ":" + System.nanoTime())
            .getBytes(StandardCharsets.UTF_8));
    String msg = preview(message);
    String body =
        "Khách phản hồi ticket \""
            + displaySubject(ticket)
            + "\"."
            + (msg.isEmpty() ? "" : " Nội dung: " + msg);
    publishTicket(eventId, TEMPLATE_SUPPORT_CUSTOMER_REPLY, body, ticket.getId());
  }

  private void publishTicket(UUID eventId, String template, String body, UUID ticketId) {
    publishQuietly(
        eventId,
        template,
        body,
        ACTION_SUPPORT_TICKET,
        ticketId.toString(),
        adminTicketPath(ticketId));
  }

  public static String adminTicketPath(UUID ticketId) {
    return "/admin/support-tickets?ticketId=" + ticketId;
  }

  private void publishQuietly(
      UUID eventId,
      String template,
      String body,
      String actionType,
      String actionId,
      String actionPath) {
    try {
      notificationClient.createOpsAlert(
          new CreateOpsAlertRequest(
              eventId.toString(), template, body, actionType, actionId, actionPath),
          apiKey);
    } catch (Exception ex) {
      log.warn("Failed to publish OPS alert template={}: {}", template, ex.getMessage());
    }
  }

  private static String displaySubject(SupportTicketEntity ticket) {
    String s = ticket.getSubject();
    if (s != null && !s.isBlank()) {
      return s.trim();
    }
    return "hỗ trợ";
  }

  private static String displayName(String fullName) {
    if (fullName != null && !fullName.isBlank()) {
      return fullName.trim();
    }
    return "khách hàng";
  }

  private static String nullToDash(String v) {
    return v == null || v.isBlank() ? "—" : v.trim();
  }

  private static String preview(String v) {
    if (v == null || v.isBlank()) {
      return "";
    }
    String t = v.trim().replaceAll("\\s+", " ");
    return t.length() <= 140 ? t : t.substring(0, 137) + "...";
  }
}
