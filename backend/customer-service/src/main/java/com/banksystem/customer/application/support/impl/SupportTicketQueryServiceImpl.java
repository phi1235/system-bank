package com.banksystem.customer.application.support.impl;
import com.banksystem.customer.application.customer.*;
import com.banksystem.customer.application.support.*;
import com.banksystem.customer.application.dashboard.*;
import com.banksystem.customer.domain.customer.*;
import com.banksystem.customer.domain.support.*;
import com.banksystem.customer.api.dto.*;

import com.banksystem.common.api.PageResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.customer.api.dto.SupportTicketDtos.AdminTicketFilterRequest;
import com.banksystem.customer.api.dto.SupportTicketDtos.SupportTicketResponse;
import com.banksystem.customer.application.mapper.SupportTicketMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupportTicketQueryServiceImpl implements SupportTicketQueryService {

  private final SupportTicketRepository ticketRepository;
  private final SupportTicketMessageRepository messageRepository;
  private final SupportTicketMapper mapper;

  public SupportTicketQueryServiceImpl(
      SupportTicketRepository ticketRepository,
      SupportTicketMessageRepository messageRepository,
      SupportTicketMapper mapper) {
    this.ticketRepository = ticketRepository;
    this.messageRepository = messageRepository;
    this.mapper = mapper;
  }

  @Transactional(readOnly = true)
  public PageResponse<SupportTicketResponse> listMine(UUID userId, Integer page, Integer size) {
    int pg = page != null ? page : 0;
    int sz = size != null ? size : 20;
    PageRequest pr = PageRequest.of(Math.max(pg, 0), clampSize(sz));
    Page<SupportTicketEntity> p = ticketRepository.findByUserIdOrderByCreatedAtDesc(userId, pr);
    return toPage(p, false);
  }

  @Transactional(readOnly = true)
  public SupportTicketResponse getMine(UUID userId, UUID ticketId) {
    SupportTicketEntity t = ticketRepository
        .findByIdAndUserId(ticketId, userId)
        .orElseThrow(() -> new BusinessException("TICKET_NOT_FOUND", "Ticket not found"));
    return toResponse(t, true);
  }

  @Transactional(readOnly = true)
  public PageResponse<SupportTicketResponse> adminList(AdminTicketFilterRequest req) {
    return adminList(TicketSearchQuery.of(req));
  }

  @Transactional(readOnly = true)
  public PageResponse<SupportTicketResponse> adminList(TicketSearchQuery query) {
    PageRequest pr = PageRequest.of(query.page(), query.size());
    Page<SupportTicketEntity> p = ticketRepository.search(
        query.statusNorm(),
        query.categoryNorm(),
        query.qNorm(),
        pr);
    return toPage(p, false);
  }

  @Transactional(readOnly = true)
  public SupportTicketResponse adminGet(UUID ticketId) {
    return toResponse(require(ticketId), true);
  }

  public SupportTicketEntity require(UUID id) {
    return ticketRepository.findById(id)
        .orElseThrow(() -> new BusinessException("TICKET_NOT_FOUND", "Ticket not found"));
  }

  private PageResponse<SupportTicketResponse> toPage(Page<SupportTicketEntity> p, boolean withMessages) {
    List<SupportTicketResponse> items = p.getContent().stream()
        .map(t -> toResponse(t, withMessages))
        .toList();
    return new PageResponse<>(items, p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
  }

  private SupportTicketResponse toResponse(SupportTicketEntity t, boolean withMessages) {
    List<SupportTicketMessageEntity> messages = withMessages
        ? messageRepository.findByTicketIdOrderByCreatedAtAsc(t.getId())
        : List.of();
    return mapper.toResponse(t, messages);
  }

  private static int clampSize(int size) {
    if (size < 1) {
      return 20;
    }
    return Math.min(size, 100);
  }
}
