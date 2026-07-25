package com.banksystem.customer.application;

import com.banksystem.common.api.PageResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.customer.api.dto.SupportTicketDtos.CreateSupportTicketRequest;
import com.banksystem.customer.api.dto.SupportTicketDtos.PostMessageRequest;
import com.banksystem.customer.api.dto.SupportTicketDtos.RejectTicketRequest;
import com.banksystem.customer.api.dto.SupportTicketDtos.RequestInfoRequest;
import com.banksystem.customer.api.dto.SupportTicketDtos.ResolveTicketRequest;
import com.banksystem.customer.api.dto.SupportTicketDtos.SupportTicketMessageResponse;
import com.banksystem.customer.api.dto.SupportTicketDtos.SupportTicketResponse;
import com.banksystem.customer.domain.CustomerEntity;
import com.banksystem.customer.domain.CustomerRepository;
import com.banksystem.customer.domain.SupportTicketEntity;
import com.banksystem.customer.domain.SupportTicketMessageEntity;
import com.banksystem.customer.domain.SupportTicketMessageRepository;
import com.banksystem.customer.domain.SupportTicketRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupportTicketService {

  private static final Set<String> CATEGORIES =
      Set.of("GENERAL", "ACCOUNT", "TRANSFER", "CARD", "KYC", "SECURITY", "OTHER");
  private static final Set<String> PRIORITIES = Set.of("LOW", "NORMAL", "HIGH");
  /** Count toward customer open-ticket limit. */
  private static final Set<String> OPEN_COUNT_STATUSES =
      Set.of("OPEN", "IN_PROGRESS", "WAITING_CUSTOMER");
  private static final int MAX_OPEN_PER_USER = 10;
  private static final String ROLE_CUSTOMER = "CUSTOMER";
  private static final String ROLE_STAFF = "STAFF";
  /** @mention tokens: email or UUID (any resolvable customer user). */
    private static final Pattern MENTION_PATTERN =
        Pattern.compile(
            "(?i)@([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}|[A-Z0-9._%+-]+@[A-Z0-9.-]+[.][A-Z]{2,})");

    private final SupportTicketRepository ticketRepository;
  private final SupportTicketMessageRepository messageRepository;
  private final CustomerRepository customerRepository;
  private final OpsAlertPublisher opsAlertPublisher;
  private final CustomerNotifyPublisher customerNotifyPublisher;

  public SupportTicketService(
      SupportTicketRepository ticketRepository,
      SupportTicketMessageRepository messageRepository,
      CustomerRepository customerRepository,
      OpsAlertPublisher opsAlertPublisher,
      CustomerNotifyPublisher customerNotifyPublisher) {
    this.ticketRepository = ticketRepository;
    this.messageRepository = messageRepository;
    this.customerRepository = customerRepository;
    this.opsAlertPublisher = opsAlertPublisher;
    this.customerNotifyPublisher = customerNotifyPublisher;
  }

  @Transactional
  public SupportTicketResponse create(UUID userId, CreateSupportTicketRequest req) {
    String category = normalizeCategory(req.category());
    String priority = normalizePriority(req.priority());
    String subject = req.subject().trim();
    String body = req.body().trim();
    if (subject.isBlank() || body.isBlank()) {
      throw new BusinessException("INVALID_REQUEST", "Subject and body are required", HttpStatus.BAD_REQUEST);
    }

    long openCount = 0;
    for (String st : OPEN_COUNT_STATUSES) {
      openCount += ticketRepository.countByUserIdAndStatus(userId, st);
    }
    if (openCount >= MAX_OPEN_PER_USER) {
      throw new BusinessException(
          "TICKET_LIMIT",
          "Too many open support tickets (max " + MAX_OPEN_PER_USER + ")",
          HttpStatus.CONFLICT);
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
    // Seed thread with original customer body as first message for continuity.
    appendMessage(saved.getId(), userId, ROLE_CUSTOMER, body);
    opsAlertPublisher.supportTicketOpened(saved);
    return toResponse(saved, true);
  }

  @Transactional(readOnly = true)
  public PageResponse<SupportTicketResponse> listMine(UUID userId, int page, int size) {
    PageRequest pr = PageRequest.of(Math.max(page, 0), clampSize(size));
    Page<SupportTicketEntity> p = ticketRepository.findByUserIdOrderByCreatedAtDesc(userId, pr);
    return toPage(p, false);
  }

  @Transactional(readOnly = true)
  public SupportTicketResponse getMine(UUID userId, UUID ticketId) {
    SupportTicketEntity t = ticketRepository
        .findByIdAndUserId(ticketId, userId)
        .orElseThrow(() -> new BusinessException("TICKET_NOT_FOUND", "Ticket not found", HttpStatus.NOT_FOUND));
    return toResponse(t, true);
  }

  @Transactional(readOnly = true)
  public PageResponse<SupportTicketResponse> adminList(
      String status, String category, String q, int page, int size) {
    String st = blankToNull(status);
    if (st != null) {
      st = st.trim().toUpperCase(Locale.ROOT);
    }
    String cat = blankToNull(category);
    if (cat != null) {
      cat = normalizeCategory(cat);
    }
    String query = blankToNull(q);
    PageRequest pr = PageRequest.of(Math.max(page, 0), clampSize(size));
    Page<SupportTicketEntity> p = ticketRepository.search(st, cat, query, pr);
    return toPage(p, false);
  }

  @Transactional(readOnly = true)
  public SupportTicketResponse adminGet(UUID ticketId) {
    return toResponse(require(ticketId), true);
  }

  /**
   * Optional: staff takes ownership (OPEN → IN_PROGRESS). Not required to resolve/reject.
   * Same staff may later decide the ticket (no 4-eyes on CS tickets).
   */
  @Transactional
  public SupportTicketResponse claim(UUID ticketId, UUID staffId) {
    SupportTicketEntity t = require(ticketId);
    if (!"OPEN".equals(t.getStatus())) {
      throw new BusinessException(
          "TICKET_NOT_CLAIMABLE",
          "Only OPEN tickets can be taken for handling",
          HttpStatus.CONFLICT);
    }
    if (staffId.equals(t.getUserId())) {
      throw new BusinessException(
          "SELF_SERVICE_FORBIDDEN",
          "Requester cannot handle own ticket",
          HttpStatus.FORBIDDEN);
    }
    t.setStatus("IN_PROGRESS");
    t.setAssignedTo(staffId);
    t.setUpdatedAt(Instant.now());
    return toResponse(ticketRepository.save(t), true);
  }

  /**
   * Staff resolve: OPEN, IN_PROGRESS, or WAITING_CUSTOMER.
   * Auto-assigns handler when not yet assigned. Notifies customer (in-app).
   */
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

  /** Staff reject: same open-state rules as resolve. */
  @Transactional
  public SupportTicketResponse reject(UUID ticketId, UUID staffId, RejectTicketRequest req) {
    SupportTicketEntity t = require(ticketId);
    requireReadyForDecision(t, staffId);
    String reason = req.reason() == null ? "" : req.reason().trim();
    if (reason.isBlank()) {
      throw new BusinessException("INVALID_REQUEST", "Reject reason is required", HttpStatus.BAD_REQUEST);
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

  /**
   * Staff requests more info from customer: OPEN/IN_PROGRESS/WAITING_CUSTOMER → WAITING_CUSTOMER.
   * Appends staff message and notifies customer.
   */
  @Transactional
  public SupportTicketResponse requestInfo(UUID ticketId, UUID staffId, RequestInfoRequest req) {
    SupportTicketEntity t = require(ticketId);
    requireStaffOnOpenTicket(t, staffId);
    String message = req.message() == null ? "" : req.message().trim();
    if (message.isBlank()) {
      throw new BusinessException("INVALID_REQUEST", "Message is required", HttpStatus.BAD_REQUEST);
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

  /**
   * Customer reply while WAITING_CUSTOMER → IN_PROGRESS (or OPEN if never assigned).
   * Also allows customer message on OPEN/IN_PROGRESS (extra context).
   */
  @Transactional
  public SupportTicketResponse customerReply(UUID ticketId, UUID userId, PostMessageRequest req) {
    SupportTicketEntity t =
        ticketRepository
            .findByIdAndUserId(ticketId, userId)
            .orElseThrow(
                () -> new BusinessException("TICKET_NOT_FOUND", "Ticket not found", HttpStatus.NOT_FOUND));
    String status = t.getStatus();
    if ("RESOLVED".equals(status) || "REJECTED".equals(status)) {
      throw new BusinessException(
          "TICKET_CLOSED", "Cannot message a closed ticket", HttpStatus.CONFLICT);
    }
    String body = req.body() == null ? "" : req.body().trim();
    if (body.isBlank()) {
      throw new BusinessException("INVALID_REQUEST", "Message body is required", HttpStatus.BAD_REQUEST);
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

  /**
   * Staff posts a message without changing status (except OPEN stays OPEN unless already claimed).
   * Notifies customer for staff replies on open tickets.
   */
  @Transactional
  public SupportTicketResponse staffMessage(UUID ticketId, UUID staffId, PostMessageRequest req) {
    SupportTicketEntity t = require(ticketId);
    requireStaffOnOpenTicket(t, staffId);
    String body = req.body() == null ? "" : req.body().trim();
    if (body.isBlank()) {
      throw new BusinessException("INVALID_REQUEST", "Message body is required", HttpStatus.BAD_REQUEST);
    }
    if (t.getAssignedTo() == null) {
      t.setAssignedTo(staffId);
    }
    // Keep WAITING_CUSTOMER if already waiting; otherwise ensure IN_PROGRESS when messaging.
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
    return ticketRepository
        .findById(id)
        .orElseThrow(() -> new BusinessException("TICKET_NOT_FOUND", "Ticket not found", HttpStatus.NOT_FOUND));
  }

  /** Allow decide on OPEN / IN_PROGRESS / WAITING_CUSTOMER; block closed + self-service. */
  private static void requireReadyForDecision(SupportTicketEntity t, UUID staffId) {
    String status = t.getStatus();
    if (!"OPEN".equals(status)
        && !"IN_PROGRESS".equals(status)
        && !"WAITING_CUSTOMER".equals(status)) {
      throw new BusinessException(
          "TICKET_NOT_OPEN",
          "Only OPEN, IN_PROGRESS or WAITING_CUSTOMER tickets can be resolved/rejected",
          HttpStatus.CONFLICT);
    }
    if (staffId.equals(t.getUserId())) {
      throw new BusinessException(
          "SELF_SERVICE_FORBIDDEN",
          "Requester cannot decide own ticket",
          HttpStatus.FORBIDDEN);
    }
  }

  private static void requireStaffOnOpenTicket(SupportTicketEntity t, UUID staffId) {
    String status = t.getStatus();
    if ("RESOLVED".equals(status) || "REJECTED".equals(status)) {
      throw new BusinessException(
          "TICKET_CLOSED", "Ticket is closed", HttpStatus.CONFLICT);
    }
    if (staffId.equals(t.getUserId())) {
      throw new BusinessException(
          "SELF_SERVICE_FORBIDDEN",
          "Requester cannot handle own ticket",
          HttpStatus.FORBIDDEN);
    }
  }

  private String resolveEmail(UUID userId) {
    return customerRepository
        .findById(userId)
        .map(CustomerEntity::getEmail)
        .filter(e -> e != null && !e.isBlank())
        .orElse(null);
  }

  private static String normalizeCategory(String raw) {
    String c = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    if (!CATEGORIES.contains(c)) {
      throw new BusinessException(
          "INVALID_CATEGORY",
          "Category must be one of " + CATEGORIES,
          HttpStatus.BAD_REQUEST);
    }
    return c;
  }

  private static String normalizePriority(String raw) {
    if (raw == null || raw.isBlank()) {
      return "NORMAL";
    }
    String p = raw.trim().toUpperCase(Locale.ROOT);
    if (!PRIORITIES.contains(p)) {
      throw new BusinessException(
          "INVALID_PRIORITY", "Priority must be one of " + PRIORITIES, HttpStatus.BAD_REQUEST);
    }
    return p;
  }

  private static int clampSize(int size) {
    if (size < 1) {
      return 20;
    }
    return Math.min(size, 100);
  }

  private static String blankToNull(String v) {
    if (v == null || v.isBlank()) {
      return null;
    }
    return v.trim();
  }

  private PageResponse<SupportTicketResponse> toPage(Page<SupportTicketEntity> p, boolean withMessages) {
    List<SupportTicketResponse> items =
        p.getContent().stream().map(t -> toResponse(t, withMessages)).toList();
    return new PageResponse<>(items, p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
  }

  private SupportTicketResponse toResponse(SupportTicketEntity t, boolean withMessages) {
    List<SupportTicketMessageResponse> messages =
        withMessages
            ? messageRepository.findByTicketIdOrderByCreatedAtAsc(t.getId()).stream()
                .map(SupportTicketService::toMessageResponse)
                .toList()
            : List.of();
    return new SupportTicketResponse(
        t.getId().toString(),
        t.getUserId().toString(),
        t.getCategory(),
        t.getSubject(),
        t.getBody(),
        t.getPriority(),
        t.getStatus(),
        t.getRequesterEmail(),
        t.getResolutionNote(),
        t.getRejectReason(),
        t.getAssignedTo() == null ? null : t.getAssignedTo().toString(),
        t.getCreatedAt(),
        t.getUpdatedAt(),
        t.getResolvedAt(),
        t.getResolvedBy() == null ? null : t.getResolvedBy().toString(),
        t.getRejectedAt(),
        t.getRejectedBy() == null ? null : t.getRejectedBy().toString(),
        messages);
  }

  private static SupportTicketMessageResponse toMessageResponse(SupportTicketMessageEntity m) {
    return new SupportTicketMessageResponse(
        m.getId().toString(),
        m.getTicketId().toString(),
        m.getAuthorUserId().toString(),
        m.getAuthorRole(),
        m.getBody(),
        m.getCreatedAt());
  }

  /**
   * Parse {@code @email} / {@code @uuid} mentions and notify resolved customer users.
   * Best-effort; skips author and unresolved tokens.
   */
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
      // not a UUID — try email
    }
    return customerRepository.findByEmailIgnoreCase(token).orElse(null);
  }
}

