package com.banksystem.customer.application;

import com.banksystem.common.api.PageResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.customer.api.dto.SupportTicketDtos.CreateSupportTicketRequest;
import com.banksystem.customer.api.dto.SupportTicketDtos.RejectTicketRequest;
import com.banksystem.customer.api.dto.SupportTicketDtos.ResolveTicketRequest;
import com.banksystem.customer.api.dto.SupportTicketDtos.SupportTicketResponse;
import com.banksystem.customer.domain.CustomerEntity;
import com.banksystem.customer.domain.CustomerRepository;
import com.banksystem.customer.domain.SupportTicketEntity;
import com.banksystem.customer.domain.SupportTicketRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
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
  private static final Set<String> OPEN_STATUSES = Set.of("OPEN", "IN_PROGRESS");
  private static final int MAX_OPEN_PER_USER = 10;

  private final SupportTicketRepository ticketRepository;
  private final CustomerRepository customerRepository;
  private final OpsAlertPublisher opsAlertPublisher;

  public SupportTicketService(
      SupportTicketRepository ticketRepository,
      CustomerRepository customerRepository,
      OpsAlertPublisher opsAlertPublisher) {
    this.ticketRepository = ticketRepository;
    this.customerRepository = customerRepository;
    this.opsAlertPublisher = opsAlertPublisher;
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

    long openCount = ticketRepository.countByUserIdAndStatus(userId, "OPEN")
        + ticketRepository.countByUserIdAndStatus(userId, "IN_PROGRESS");
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
    opsAlertPublisher.supportTicketOpened(saved);
    return toResponse(saved);
  }

  @Transactional(readOnly = true)
  public PageResponse<SupportTicketResponse> listMine(UUID userId, int page, int size) {
    PageRequest pr = PageRequest.of(Math.max(page, 0), clampSize(size));
    Page<SupportTicketEntity> p = ticketRepository.findByUserIdOrderByCreatedAtDesc(userId, pr);
    return toPage(p);
  }

  @Transactional(readOnly = true)
  public SupportTicketResponse getMine(UUID userId, UUID ticketId) {
    SupportTicketEntity t = ticketRepository
        .findByIdAndUserId(ticketId, userId)
        .orElseThrow(() -> new BusinessException("TICKET_NOT_FOUND", "Ticket not found", HttpStatus.NOT_FOUND));
    return toResponse(t);
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
    return toPage(p);
  }

  @Transactional(readOnly = true)
  public SupportTicketResponse adminGet(UUID ticketId) {
    return toResponse(require(ticketId));
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
    return toResponse(ticketRepository.save(t));
  }

  /**
   * Staff resolve: OPEN or IN_PROGRESS. Handler may be the same person who claimed (if any).
   * Auto-assigns handler when not yet assigned.
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
    return toResponse(saved);
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
    return toResponse(saved);
  }

  private SupportTicketEntity require(UUID id) {
    return ticketRepository
        .findById(id)
        .orElseThrow(() -> new BusinessException("TICKET_NOT_FOUND", "Ticket not found", HttpStatus.NOT_FOUND));
  }

  /** Allow decide on OPEN or IN_PROGRESS; block closed tickets and self-service by requester. */
  private static void requireReadyForDecision(SupportTicketEntity t, UUID staffId) {
    String status = t.getStatus();
    if (!"OPEN".equals(status) && !"IN_PROGRESS".equals(status)) {
      throw new BusinessException(
          "TICKET_NOT_OPEN",
          "Only OPEN or IN_PROGRESS tickets can be resolved/rejected",
          HttpStatus.CONFLICT);
    }
    if (staffId.equals(t.getUserId())) {
      throw new BusinessException(
          "SELF_SERVICE_FORBIDDEN",
          "Requester cannot decide own ticket",
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

  private static PageResponse<SupportTicketResponse> toPage(Page<SupportTicketEntity> p) {
    List<SupportTicketResponse> items = p.getContent().stream().map(SupportTicketService::toResponse).toList();
    return new PageResponse<>(items, p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
  }

  private static SupportTicketResponse toResponse(SupportTicketEntity t) {
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
        t.getRejectedBy() == null ? null : t.getRejectedBy().toString());
  }
}
