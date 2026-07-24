package com.banksystem.customer.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.customer.api.dto.SupportTicketDtos.CreateSupportTicketRequest;
import com.banksystem.customer.api.dto.SupportTicketDtos.RejectTicketRequest;
import com.banksystem.customer.api.dto.SupportTicketDtos.ResolveTicketRequest;
import com.banksystem.customer.domain.CustomerEntity;
import com.banksystem.customer.domain.CustomerRepository;
import com.banksystem.customer.domain.SupportTicketEntity;
import com.banksystem.customer.domain.SupportTicketRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SupportTicketServiceTest {

  private SupportTicketRepository ticketRepository;
  private CustomerRepository customerRepository;
  private OpsAlertPublisher opsAlertPublisher;
  private SupportTicketService service;
  private final UUID userId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
  private final UUID staffId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
  private final UUID otherStaffId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

  @BeforeEach
  void setUp() {
    ticketRepository = mock(SupportTicketRepository.class);
    customerRepository = mock(CustomerRepository.class);
    opsAlertPublisher = mock(OpsAlertPublisher.class);
    service = new SupportTicketService(ticketRepository, customerRepository, opsAlertPublisher);
  }

  @Test
  void create_opensTicketAndAlertsOps() {
    CustomerEntity c = new CustomerEntity();
    c.setId(userId);
    c.setEmail("alice@bank.vn");
    when(customerRepository.findById(userId)).thenReturn(Optional.of(c));
    when(ticketRepository.countByUserIdAndStatus(userId, "OPEN")).thenReturn(0L);
    when(ticketRepository.countByUserIdAndStatus(userId, "IN_PROGRESS")).thenReturn(0L);
    when(ticketRepository.save(any(SupportTicketEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    var res =
        service.create(
            userId,
            new CreateSupportTicketRequest("TRANSFER", "Sai phí", "Mình bị trừ phí lạ", "HIGH"));

    assertEquals("OPEN", res.status());
    assertEquals("TRANSFER", res.category());
    assertEquals("HIGH", res.priority());
    assertEquals("alice@bank.vn", res.requesterEmail());
    ArgumentCaptor<SupportTicketEntity> cap = ArgumentCaptor.forClass(SupportTicketEntity.class);
    verify(ticketRepository).save(cap.capture());
    verify(opsAlertPublisher).supportTicketOpened(cap.getValue());
  }

  @Test
  void create_rejectsInvalidCategory() {
    when(ticketRepository.countByUserIdAndStatus(userId, "OPEN")).thenReturn(0L);
    when(ticketRepository.countByUserIdAndStatus(userId, "IN_PROGRESS")).thenReturn(0L);
    assertThrows(
        BusinessException.class,
        () ->
            service.create(
                userId, new CreateSupportTicketRequest("LOAN", "x", "body enough", "NORMAL")));
  }

  @Test
  void claim_optional_movesOpenToInProgress() {
    SupportTicketEntity t = openTicket();
    when(ticketRepository.findById(t.getId())).thenReturn(Optional.of(t));
    when(ticketRepository.save(any(SupportTicketEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    var res = service.claim(t.getId(), staffId);
    assertEquals("IN_PROGRESS", res.status());
    assertEquals(staffId.toString(), res.assignedTo());
  }

  @Test
  void claim_rejectsSelfService() {
    SupportTicketEntity t = openTicket();
    when(ticketRepository.findById(t.getId())).thenReturn(Optional.of(t));
    BusinessException ex =
        assertThrows(BusinessException.class, () -> service.claim(t.getId(), userId));
    assertEquals("SELF_SERVICE_FORBIDDEN", ex.getCode());
  }

  @Test
  void resolve_directlyFromOpen_assignsHandler() {
    SupportTicketEntity t = openTicket();
    when(ticketRepository.findById(t.getId())).thenReturn(Optional.of(t));
    when(ticketRepository.save(any(SupportTicketEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    var res = service.resolve(t.getId(), staffId, new ResolveTicketRequest("Đã hoàn phí"));
    assertEquals("RESOLVED", res.status());
    assertEquals("Đã hoàn phí", res.resolutionNote());
    assertEquals(staffId.toString(), res.resolvedBy());
    assertEquals(staffId.toString(), res.assignedTo());
    verify(opsAlertPublisher).supportTicketResolved(any(SupportTicketEntity.class));
  }

  @Test
  void resolve_allowsSameStaffWhoClaimed() {
    SupportTicketEntity t = claimedTicket();
    when(ticketRepository.findById(t.getId())).thenReturn(Optional.of(t));
    when(ticketRepository.save(any(SupportTicketEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    var res = service.resolve(t.getId(), staffId, new ResolveTicketRequest("self handle ok"));
    assertEquals("RESOLVED", res.status());
    assertEquals(staffId.toString(), res.resolvedBy());
    assertEquals(staffId.toString(), res.assignedTo());
  }

  @Test
  void resolve_otherStaffCanFinishClaimedTicket() {
    SupportTicketEntity t = claimedTicket();
    when(ticketRepository.findById(t.getId())).thenReturn(Optional.of(t));
    when(ticketRepository.save(any(SupportTicketEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    var res = service.resolve(t.getId(), otherStaffId, new ResolveTicketRequest("handoff ok"));
    assertEquals("RESOLVED", res.status());
    assertEquals(otherStaffId.toString(), res.resolvedBy());
    // original assignee kept if already set
    assertEquals(staffId.toString(), res.assignedTo());
  }

  @Test
  void resolve_blocksRequesterSelfService() {
    SupportTicketEntity t = openTicket();
    when(ticketRepository.findById(t.getId())).thenReturn(Optional.of(t));
    BusinessException ex =
        assertThrows(
            BusinessException.class,
            () -> service.resolve(t.getId(), userId, new ResolveTicketRequest("self")));
    assertEquals("SELF_SERVICE_FORBIDDEN", ex.getCode());
  }

  @Test
  void reject_fromOpen_requiresReason() {
    SupportTicketEntity t = openTicket();
    when(ticketRepository.findById(t.getId())).thenReturn(Optional.of(t));
    when(ticketRepository.save(any(SupportTicketEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    var res = service.reject(t.getId(), staffId, new RejectTicketRequest("Thiếu chứng từ"));
    assertEquals("REJECTED", res.status());
    assertEquals("Thiếu chứng từ", res.rejectReason());
    assertEquals(staffId.toString(), res.assignedTo());
    verify(opsAlertPublisher).supportTicketRejected(any(SupportTicketEntity.class));
  }

  @Test
  void resolve_closedTicketConflicts() {
    SupportTicketEntity t = openTicket();
    t.setStatus("RESOLVED");
    when(ticketRepository.findById(t.getId())).thenReturn(Optional.of(t));
    BusinessException ex =
        assertThrows(
            BusinessException.class,
            () -> service.resolve(t.getId(), staffId, new ResolveTicketRequest("late")));
    assertEquals("TICKET_NOT_OPEN", ex.getCode());
  }

  private SupportTicketEntity openTicket() {
    SupportTicketEntity t = new SupportTicketEntity();
    t.setId(UUID.randomUUID());
    t.setUserId(userId);
    t.setCategory("GENERAL");
    t.setSubject("Help");
    t.setBody("Please help");
    t.setPriority("NORMAL");
    t.setStatus("OPEN");
    return t;
  }

  private SupportTicketEntity claimedTicket() {
    SupportTicketEntity t = openTicket();
    t.setStatus("IN_PROGRESS");
    t.setAssignedTo(staffId);
    return t;
  }
}
