package com.banksystem.customer.application.customer;
import com.banksystem.customer.application.customer.*;
import com.banksystem.customer.application.support.*;
import com.banksystem.customer.application.dashboard.*;
import com.banksystem.customer.domain.customer.*;
import com.banksystem.customer.domain.support.*;
import com.banksystem.customer.api.dto.*;

import com.banksystem.customer.infrastructure.feign.NotificationClient;
import com.banksystem.customer.infrastructure.feign.NotificationClientDtos.CreateNotificationLogRequest;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Best-effort customer in-app notifications (IB inbox). Never blocks support ticket decisions.
 * Body text is human-readable Vietnamese for end users — no raw ticketId/UUID dumps.
 */
@Service
public class CustomerNotifyPublisher {

  private static final Logger log = LoggerFactory.getLogger(CustomerNotifyPublisher.class);

  public static final String TEMPLATE_SUPPORT_RESOLVED = "SUPPORT_TICKET_RESOLVED";
  public static final String TEMPLATE_SUPPORT_REJECTED = "SUPPORT_TICKET_REJECTED";
  public static final String TEMPLATE_SUPPORT_NEED_INFO = "SUPPORT_TICKET_NEED_INFO";
  public static final String TEMPLATE_SUPPORT_STAFF_REPLY = "SUPPORT_TICKET_STAFF_REPLY";
  public static final String TEMPLATE_SUPPORT_MENTION = "SUPPORT_TICKET_MENTION";

  public static final String ACTION_SUPPORT_TICKET = "SUPPORT_TICKET";

  private final NotificationClient notificationClient;
  private final String apiKey;

  public CustomerNotifyPublisher(
      NotificationClient notificationClient,
      @Value("${bank.internal.notification-api-key}") String apiKey) {
    this.notificationClient = notificationClient;
    this.apiKey = apiKey;
  }

  public void supportTicketResolved(SupportTicketEntity ticket) {
    String subject = displaySubject(ticket);
    String note = ticket.getResolutionNote();
    String body =
        "Ticket \""
            + subject
            + "\" đã được giải quyết."
            + (note == null || note.isBlank() ? "" : " Ghi chú: " + preview(note));
    publishToCustomer(ticket, TEMPLATE_SUPPORT_RESOLVED, body);
  }

  public void supportTicketRejected(SupportTicketEntity ticket) {
    String subject = displaySubject(ticket);
    String reason = ticket.getRejectReason();
    String body =
        "Ticket \""
            + subject
            + "\" đã bị từ chối."
            + (reason == null || reason.isBlank() ? "" : " Lý do: " + preview(reason));
    publishToCustomer(ticket, TEMPLATE_SUPPORT_REJECTED, body);
  }

  public void supportTicketNeedInfo(SupportTicketEntity ticket, String staffMessage) {
    String subject = displaySubject(ticket);
    String msg = preview(staffMessage);
    String body =
        "Nhân viên yêu cầu bổ sung thông tin cho ticket \""
            + subject
            + "\"."
            + (msg.isEmpty() ? "" : " Nội dung: " + msg);
    publishToCustomer(ticket, TEMPLATE_SUPPORT_NEED_INFO, body);
  }

  public void supportTicketStaffReply(SupportTicketEntity ticket, String staffMessage) {
    String subject = displaySubject(ticket);
    String msg = preview(staffMessage);
    String body =
        "Nhân viên đã phản hồi ticket \""
            + subject
            + "\"."
            + (msg.isEmpty() ? "" : " Nội dung: " + msg);
    publishToCustomer(ticket, TEMPLATE_SUPPORT_STAFF_REPLY, body);
  }

  /** Notify a mentioned user (any resolved customer user id). */
  public void supportTicketMention(
      UUID mentionedUserId, String recipientEmail, SupportTicketEntity ticket, String messagePreview) {
    if (mentionedUserId == null) {
      return;
    }
    String subject = displaySubject(ticket);
    String msg = preview(messagePreview);
    String body =
        "Bạn được nhắc đến trong ticket \""
            + subject
            + "\"."
            + (msg.isEmpty() ? "" : " Nội dung: " + msg);
    String recipient =
        recipientEmail != null && !recipientEmail.isBlank()
            ? recipientEmail.trim()
            : "user-" + mentionedUserId + "@bank.local";
    publishQuietly(
        mentionedUserId,
        recipient,
        TEMPLATE_SUPPORT_MENTION,
        body,
        ACTION_SUPPORT_TICKET,
        ticket.getId().toString(),
        customerTicketPath(ticket.getId()));
  }

  private void publishToCustomer(SupportTicketEntity ticket, String template, String body) {
    publishQuietly(
        ticket.getUserId(),
        recipient(ticket),
        template,
        body,
        ACTION_SUPPORT_TICKET,
        ticket.getId().toString(),
        customerTicketPath(ticket.getId()));
  }

  private void publishQuietly(
      UUID userId,
      String recipient,
      String template,
      String body,
      String actionType,
      String actionId,
      String actionPath) {
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
              "CUSTOMER",
              actionType,
              actionId,
              actionPath),
          apiKey);
    } catch (Exception ex) {
      log.warn("Failed to publish customer notification template={}: {}", template, ex.getMessage());
    }
  }

  public static String customerTicketPath(UUID ticketId) {
    return "/customer/support?ticketId=" + ticketId;
  }

  private static String recipient(SupportTicketEntity ticket) {
    if (ticket.getRequesterEmail() != null && !ticket.getRequesterEmail().isBlank()) {
      return ticket.getRequesterEmail().trim();
    }
    return "user-" + ticket.getUserId() + "@bank.local";
  }

  /** Subject shown to end users — never fall back to raw UUID. */
  private static String displaySubject(SupportTicketEntity ticket) {
    String s = ticket.getSubject();
    if (s != null && !s.isBlank()) {
      return s.trim();
    }
    return "hỗ trợ";
  }

  private static String preview(String v) {
    if (v == null || v.isBlank()) {
      return "";
    }
    String t = v.trim().replaceAll("\\s+", " ");
    return t.length() <= 140 ? t : t.substring(0, 137) + "...";
  }
}
