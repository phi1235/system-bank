package com.banksystem.customer.application.support.impl;
import com.banksystem.customer.application.customer.*;
import com.banksystem.customer.application.support.*;
import com.banksystem.customer.application.dashboard.*;
import com.banksystem.customer.domain.customer.*;
import com.banksystem.customer.domain.support.*;
import com.banksystem.customer.api.dto.*;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.customer.api.dto.SupportTicketDtos.CreateSupportTicketRequest;
import com.banksystem.customer.api.dto.SupportTicketDtos.PostMessageRequest;
import com.banksystem.customer.api.dto.SupportTicketDtos.RejectTicketRequest;
import com.banksystem.customer.api.dto.SupportTicketDtos.RequestInfoRequest;
import com.banksystem.customer.api.dto.SupportTicketDtos.ResolveTicketRequest;
import com.banksystem.customer.api.dto.SupportTicketDtos.SupportTicketResponse;
import com.banksystem.customer.application.mapper.SupportTicketMapper;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupportTicketCommandServiceImpl implements SupportTicketCommandService {

  private static final Set<String> CATEGORIES =
      Set.of("GENERAL", "ACCOUNT", "TRANSFER", "CARD", "KYC", "SECURITY", "OTHER");
  private static final Set<String> PRIORITIES = Set.of("LOW", "NORMAL", "HIGH");
  private static final Set<String> OPEN_COUNT_STATUSES =
      Set.of("OPEN", "IN_PROGRESS", "WAITING_CUSTOMER");
  private static final int MAX_OPEN_PER_USER = 10;
  private static final String ROLE_CUSTOMER = "CUSTOMER";
  private static final String ROLE_STAFF = "STAFF";

  private static final Pattern MENTION_PATTERN =
      Pattern.compile(
          "(?i)@([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}|[A-Z0-9._%+-]+@[A-Z0-9.-]+[.][A-Z]{2,})");

  private final SupportTicketRepository ticketRepository;
  private final SupportTicketMessageRepository messageRepository;
  private final CustomerRepository customerRepository;
  private final OpsAlertPublisher opsAlertPublisher;
  private final CustomerNotifyPublisher customerNotifyPublisher;
  private final SupportTicketMapper mapper;

  public SupportTicketCommandServiceImpl(
      SupportTicketRepository ticketRepository,
      SupportTicketMessageRepository messageRepository,
      CustomerRepository customerRepository,
      OpsAlertPublisher opsAlertPublisher,
      CustomerNotifyPublisher customerNotifyPublisher,
      SupportTicketMapper mapper) {
    this.ticketRepository = ticketRepository;
    this.messageRepository = messageRepository;
    this.customerRepository = customerRepository;
    this.opsAlertPublisher = opsAlertPublisher;
    this.customerNotifyPublisher = customerNotifyPublisher;
    this.mapper = mapper;
  }

  @Transactional
  public SupportTicketResponse create(UUID userId, CreateSupportTicketRequest req) {
    String category = normalizeCategory(req.category());
    String priority = normalizePriority(req.priority());
    String subject = req.subject().trim();
    String body = req.body().trim();
    if (subject.isBlank() || body.isBlank()) {
      throw new BusinessException("INVALID_REQUEST", "Subject and body are required");
    }

    long openCount = 0;
    for (String st : OPEN_COUNT_STATUSES) {
      openCount += ticketRepository.countByUserIdAndStatus(userId, st);
    }
    if (openCount >= MAX_OPEN_PER_USER) {
      throw new BusinessException(
          "TICKET_LIMIT",
          "Too many open support tickets (max " + MAX_OPEN_PER_USER + ")");
    }

    SupportTicketEntity t = new SupportTicketEntity();
    t.setId(UUID.randomUUID());
    t.setUserId(userId);
    t.setCategory(category);
    t.setSubject(subject);
    t.setBody(body);
    t.setPriority(priority);
    t.setStatus("OPEN");
    t.setRequesterEmail(resolveEmail(userId));
    Instant now = Instant.now();
    t.setCreatedAt(now);
    t.setUpdatedAt(now);
    SupportTicketEntity saved = ticketRepository.save(t);
    appendMessage(saved.getId(), userId, ROLE_CUSTOMER, body);
    opsAlertPublisher.supportTicketOpened(saved);
    return toResponse(saved, true);
  }

  @Transactional
  public SupportTicketResponse claim(UUID ticketId, UUID staffId) {
    SupportTicketEntity t = require(ticketId);
    if (!"OPEN".equals(t.getStatus())) {
      throw new BusinessException(
          "TICKET_NOT_CLAIMABLE",
          "Only OPEN tickets can be taken for handling");
    }
    if (staffId.equals(t.getUserId())) {
      throw new BusinessException(
          "SELF_SERVICE_FORBIDDEN",
          "Requester cannot handle own ticket");
    }
    t.setStatus("IN_PROGRESS");
    t.setAssignedTo(staffId);
    t.setUpdatedAt(Instant.now());
    return toResponse(ticketRepository.save(t), true);
  }

  @Transactional
  public SupportTicketResponse resolve(UUID ticketId, UUID staffId, ResolveTicketRequest req) {
    SupportTicketEntity t = require(ticketId);
    requireReadyForDecision(t, staffId);
    String note = req == null || req.resolutionNote() == null ? null : req.resolutionNote().trim();
    Instant now = Instant.now();
    if (t.getAssignedTo() == null) {
      t.setAssignedTo(staffId);
    }
    t.setStatus("RESOLVED");
    t.setResolutionNote(note == null || note.isBlank() ? null : note);
    t.setResolvedAt(now);
    t.setResolvedBy(staffId);
    t.setUpdatedAt(now);
    SupportTicketEntity saved = ticketRepository.save(t);
    opsAlertPublisher.supportTicketResolved(saved);
    customerNotifyPublisher.supportTicketResolved(saved);
    return toResponse(saved, true);
  }

  @Transactional
  public SupportTicketResponse reject(UUID ticketId, UUID staffId, RejectTicketRequest req) {
    SupportTicketEntity t = require(ticketId);
    requireReadyForDecision(t, staffId);
    String reason = req.reason() == null ? "" : req.reason().trim();
    if (reason.isBlank()) {
      throw new BusinessException("INVALID_REQUEST", "Reject reason is required");
    }
    Instant now = Instant.now();
    if (t.getAssignedTo() == null) {
      t.setAssignedTo(staffId);
    }
    t.setStatus("REJECTED");
    t.setRejectReason(reason);
    t.setRejectedAt(now);
    t.setRejectedBy(staffId);
    t.setUpdatedAt(now);
    SupportTicketEntity saved = ticketRepository.save(t);
    opsAlertPublisher.supportTicketRejected(saved);
    customerNotifyPublisher.supportTicketRejected(saved);
    return toResponse(saved, true);
  }

  @Transactional
  public SupportTicketResponse requestInfo(UUID ticketId, UUID staffId, RequestInfoRequest req) {
    SupportTicketEntity t = require(ticketId);
    requireStaffOnOpenTicket(t, staffId);
    String message = req.message() == null ? "" : req.message().trim();
    if (message.isBlank()) {
      throw new BusinessException("INVALID_REQUEST", "Message is required");
    }
    Instant now = Instant.now();
    if (t.getAssignedTo() == null) {
      t.setAssignedTo(staffId);
    }
    t.setStatus("WAITING_CUSTOMER");
    t.setUpdatedAt(now);
    SupportTicketEntity saved = ticketRepository.save(t);
    appendMessage(saved.getId(), staffId, ROLE_STAFF, message);
    customerNotifyPublisher.supportTicketNeedInfo(saved, message);
    notifyMentions(saved, message, staffId);
    return toResponse(saved, true);
  }

  @Transactional
  public SupportTicketResponse customerReply(UUID ticketId, UUID userId, PostMessageRequest req) {
    SupportTicketEntity t = ticketRepository.findByIdAndUserId(ticketId, userId)
        .orElseThrow(() -> new BusinessException("TICKET_NOT_FOUND", "Ticket not found"));
    String status = t.getStatus();
    if ("RESOLVED".equals(status) || "REJECTED".equals(status)) {
      throw new BusinessException("TICKET_CLOSED", "Cannot message a closed ticket");
    }
    String body = req.body() == null ? "" : req.body().trim();
    if (body.isBlank()) {
      throw new BusinessException("INVALID_REQUEST", "Message body is required");
    }
    appendMessage(t.getId(), userId, ROLE_CUSTOMER, body);
    Instant now = Instant.now();
    if ("WAITING_CUSTOMER".equals(status)) {
      t.setStatus(t.getAssignedTo() == null ? "OPEN" : "IN_PROGRESS");
    }
    t.setUpdatedAt(now);
    SupportTicketEntity saved = ticketRepository.save(t);
    opsAlertPublisher.supportTicketCustomerReply(saved, body);
    notifyMentions(saved, body, userId);
    return toResponse(saved, true);
  }

  @Transactional
  public SupportTicketResponse staffMessage(UUID ticketId, UUID staffId, PostMessageRequest req) {
    SupportTicketEntity t = require(ticketId);
    requireStaffOnOpenTicket(t, staffId);
    String body = req.body() == null ? "" : req.body().trim();
    if (body.isBlank()) {
      throw new BusinessException("INVALID_REQUEST", "Message body is required");
    }
    if (t.getAssignedTo() == null) {
      t.setAssignedTo(staffId);
    }
    if ("OPEN".equals(t.getStatus())) {
      t.setStatus("IN_PROGRESS");
    }
    t.setUpdatedAt(Instant.now());
    SupportTicketEntity saved = ticketRepository.save(t);
    appendMessage(saved.getId(), staffId, ROLE_STAFF, body);
    customerNotifyPublisher.supportTicketStaffReply(saved, body);
    notifyMentions(saved, body, staffId);
    return toResponse(saved, true);
  }

  private SupportTicketMessageEntity appendMessage(
      UUID ticketId, UUID authorUserId, String authorRole, String body) {
    SupportTicketMessageEntity m = new SupportTicketMessageEntity();
    m.setId(UUID.randomUUID());
    m.setTicketId(ticketId);
    m.setAuthorUserId(authorUserId);
    m.setAuthorRole(authorRole);
    m.setBody(body);
    m.setCreatedAt(Instant.now());
    return messageRepository.save(m);
  }

  private SupportTicketEntity require(UUID id) {
    return ticketRepository.findById(id)
        .orElseThrow(() -> new BusinessException("TICKET_NOT_FOUND", "Ticket not found"));
  }

  private static void requireReadyForDecision(SupportTicketEntity t, UUID staffId) {
    String status = t.getStatus();
    if (!"OPEN".equals(status)
        && !"IN_PROGRESS".equals(status)
        && !"WAITING_CUSTOMER".equals(status)) {
      throw new BusinessException(
          "TICKET_NOT_OPEN",
          "Only OPEN, IN_PROGRESS or WAITING_CUSTOMER tickets can be resolved/rejected");
    }
    if (staffId.equals(t.getUserId())) {
      throw new BusinessException(
          "SELF_SERVICE_FORBIDDEN",
          "Requester cannot decide own ticket");
    }
  }

  private static void requireStaffOnOpenTicket(SupportTicketEntity t, UUID staffId) {
    String status = t.getStatus();
    if ("RESOLVED".equals(status) || "REJECTED".equals(status)) {
      throw new BusinessException("TICKET_CLOSED", "Ticket is closed");
    }
    if (staffId.equals(t.getUserId())) {
      throw new BusinessException(
          "SELF_SERVICE_FORBIDDEN",
          "Requester cannot handle own ticket");
    }
  }

  private String resolveEmail(UUID userId) {
    return customerRepository.findById(userId)
        .map(CustomerEntity::getEmail)
        .filter(e -> e != null && !e.isBlank())
        .orElse(null);
  }

  private static String normalizeCategory(String raw) {
    String c = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    if (!CATEGORIES.contains(c)) {
      throw new BusinessException("INVALID_CATEGORY", "Category must be one of " + CATEGORIES);
    }
    return c;
  }

  private static String normalizePriority(String raw) {
    if (raw == null || raw.isBlank()) {
      return "NORMAL";
    }
    String p = raw.trim().toUpperCase(Locale.ROOT);
    if (!PRIORITIES.contains(p)) {
      throw new BusinessException("INVALID_PRIORITY", "Priority must be one of " + PRIORITIES);
    }
    return p;
  }

  private SupportTicketResponse toResponse(SupportTicketEntity t, boolean withMessages) {
    List<SupportTicketMessageEntity> messages = withMessages
        ? messageRepository.findByTicketIdOrderByCreatedAtAsc(t.getId())
        : List.of();
    return mapper.toResponse(t, messages);
  }

  private void notifyMentions(SupportTicketEntity ticket, String body, UUID authorUserId) {
    if (body == null || body.isBlank()) {
      return;
    }
    Matcher matcher = MENTION_PATTERN.matcher(body);
    Set<UUID> notified = new HashSet<>();
    while (matcher.find()) {
      String token = matcher.group(1);
      if (token == null || token.isBlank()) {
        continue;
      }
      CustomerEntity target = resolveMention(token.trim());
      if (target == null || target.getId() == null) {
        continue;
      }
      if (target.getId().equals(authorUserId) || !notified.add(target.getId())) {
        continue;
      }
      customerNotifyPublisher.supportTicketMention(
          target.getId(), target.getEmail(), ticket, body);
    }
  }

  private CustomerEntity resolveMention(String token) {
    try {
      UUID id = UUID.fromString(token);
      return customerRepository.findById(id).orElse(null);
    } catch (IllegalArgumentException ignored) {
    }
    return customerRepository.findByEmailIgnoreCase(token).orElse(null);
  }
}
