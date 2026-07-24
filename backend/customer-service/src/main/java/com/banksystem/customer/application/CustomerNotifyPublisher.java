package com.banksystem.customer.application;

import com.banksystem.customer.domain.SupportTicketEntity;
import com.banksystem.customer.infrastructure.feign.NotificationClient;
import com.banksystem.customer.infrastructure.feign.NotificationClientDtos.CreateNotificationLogRequest;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Best-effort customer in-app notifications (IB inbox). Never blocks support ticket decisions.
 */
@Service
public class CustomerNotifyPublisher {

  private static final Logger log = LoggerFactory.getLogger(CustomerNotifyPublisher.class);

  public static final String TEMPLATE_SUPPORT_RESOLVED = "SUPPORT_TICKET_RESOLVED";
  public static final String TEMPLATE_SUPPORT_REJECTED = "SUPPORT_TICKET_REJECTED";
  public static final String TEMPLATE_SUPPORT_NEED_INFO = "SUPPORT_TICKET_NEED_INFO";
  public static final String TEMPLATE_SUPPORT_STAFF_REPLY = "SUPPORT_TICKET_STAFF_REPLY";

  private final NotificationClient notificationClient;
  private final String apiKey;

  public CustomerNotifyPublisher(
      NotificationClient notificationClient,
      @Value("${bank.internal.notification-api-key}") String apiKey) {
    this.notificationClient = notificationClient;
    this.apiKey = apiKey;
  }

  public void supportTicketResolved(SupportTicketEntity ticket) {
    String note = ticket.getResolutionNote();
    String body =
        "Ticket resolved"
            + " ticketId="
            + ticket.getId()
            + " subject="
            + nullToNa(ticket.getSubject())
            + (note == null || note.isBlank() ? "" : " note=" + note);
    publishQuietly(
        ticket.getUserId(),
        recipient(ticket),
        TEMPLATE_SUPPORT_RESOLVED,
        body);
  }

  public void supportTicketRejected(SupportTicketEntity ticket) {
    String body =
        "Ticket rejected"
            + " ticketId="
            + ticket.getId()
            + " subject="
            + nullToNa(ticket.getSubject())
            + " reason="
            + nullToNa(ticket.getRejectReason());
    publishQuietly(
        ticket.getUserId(),
        recipient(ticket),
        TEMPLATE_SUPPORT_REJECTED,
        body);
  }

  public void supportTicketNeedInfo(SupportTicketEntity ticket, String staffMessage) {
    String body =
        "Staff requested more information"
            + " ticketId="
            + ticket.getId()
            + " subject="
            + nullToNa(ticket.getSubject())
            + " message="
            + nullToNa(staffMessage);
    publishQuietly(
        ticket.getUserId(),
        recipient(ticket),
        TEMPLATE_SUPPORT_NEED_INFO,
        body);
  }

  public void supportTicketStaffReply(SupportTicketEntity ticket, String staffMessage) {
    String body =
        "New staff reply on ticket"
            + " ticketId="
            + ticket.getId()
            + " subject="
            + nullToNa(ticket.getSubject())
            + " message="
            + nullToNa(staffMessage);
    publishQuietly(
        ticket.getUserId(),
        recipient(ticket),
        TEMPLATE_SUPPORT_STAFF_REPLY,
        body);
  }

  private void publishQuietly(UUID userId, String recipient, String template, String body) {
    if (userId == null) {
      return;
    }
    try {
      notificationClient.createNotificationLog(
          new CreateNotificationLogRequest(
              "IN_APP",
              recipient,
              template,
              "SENT",
              body,
              userId,
              "CUSTOMER"),
          apiKey);
    } catch (Exception ex) {
      log.warn("Failed to publish customer notification template={}: {}", template, ex.getMessage());
    }
  }

  private static String recipient(SupportTicketEntity ticket) {
    if (ticket.getRequesterEmail() != null && !ticket.getRequesterEmail().isBlank()) {
      return ticket.getRequesterEmail().trim();
    }
    return "user-" + ticket.getUserId() + "@bank.local";
  }

  private static String nullToNa(String v) {
    return v == null || v.isBlank() ? "n/a" : v;
  }
}
