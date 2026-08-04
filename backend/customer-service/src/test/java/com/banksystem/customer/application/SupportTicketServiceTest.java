package com.banksystem.customer.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.customer.api.dto.SupportTicketDtos.CreateSupportTicketRequest;
import com.banksystem.customer.api.dto.SupportTicketDtos.PostMessageRequest;
import com.banksystem.customer.api.dto.SupportTicketDtos.RejectTicketRequest;
import com.banksystem.customer.api.dto.SupportTicketDtos.RequestInfoRequest;
import com.banksystem.customer.api.dto.SupportTicketDtos.ResolveTicketRequest;
import com.banksystem.customer.application.command.SupportTicketCommandService;
import com.banksystem.customer.application.mapper.SupportTicketMapper;
import com.banksystem.customer.domain.CustomerEntity;
import com.banksystem.customer.domain.CustomerRepository;
import com.banksystem.customer.domain.SupportTicketEntity;
import com.banksystem.customer.domain.SupportTicketMessageEntity;
import com.banksystem.customer.domain.SupportTicketMessageRepository;
import com.banksystem.customer.domain.SupportTicketRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SupportTicketServiceTest {

  private SupportTicketRepository ticketRepository;
  private SupportTicketMessageRepository messageRepository;
  private CustomerRepository customerRepository;
  private OpsAlertPublisher opsAlertPublisher;
  private CustomerNotifyPublisher customerNotifyPublisher;
  private SupportTicketCommandService service;
  private final UUID userId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
  private final UUID staffId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

  @BeforeEach
  void setUp() {
    ticketRepository = mock(SupportTicketRepository.class);
    messageRepository = mock(SupportTicketMessageRepository.class);
    customerRepository = mock(CustomerRepository.class);
    opsAlertPublisher = mock(OpsAlertPublisher.class);
    customerNotifyPublisher = mock(CustomerNotifyPublisher.class);
    SupportTicketMapper mapper = new SupportTicketMapper();
    service =
        new SupportTicketCommandService(
            ticketRepository,
            messageRepository,
            customerRepository,
            opsAlertPublisher,
            customerNotifyPublisher,
            mapper);
    when(messageRepository.save(any(SupportTicketMessageEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(messageRepository.findByTicketIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
  }

  @Test
  void create_savesTicketAndFirstMessage() {
    when(customerRepository.findById(userId)).thenReturn(Optional.of(sampleCustomer()));
    when(ticketRepository.save(any(SupportTicketEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    var res =
        service.create(
            userId,
            new CreateSupportTicketRequest(" transfer ", " Can not transfer ", " Body text ", " high "));

    assertEquals("TRANSFER", res.category());
    assertEquals("HIGH", res.priority());
    assertEquals("OPEN", res.status());
    assertEquals("alice@example.com", res.requesterEmail());
    verify(messageRepository).save(any(SupportTicketMessageEntity.class));
    verify(opsAlertPublisher).supportTicketOpened(any(SupportTicketEntity.class));
  }

  @Test
  void create_exceedsLimit_throws() {
    when(ticketRepository.countByUserIdAndStatus(userId, "OPEN")).thenReturn(10L);
    BusinessException ex =
        assertThrows(
            BusinessException.class,
            () ->
                service.create(
                    userId,
                    new CreateSupportTicketRequest("GENERAL", "Title", "Body", "NORMAL")));
    assertEquals("TICKET_LIMIT", ex.getCode());
  }

  @Test
  void claim_openTicket_success() {
    SupportTicketEntity t = openTicket();
    when(ticketRepository.findById(t.getId())).thenReturn(Optional.of(t));
    when(ticketRepository.save(any(SupportTicketEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    var res = service.claim(t.getId(), staffId);

    assertEquals("IN_PROGRESS", res.status());
    assertEquals(staffId.toString(), res.assignedTo());
  }

  @Test
  void claim_requesterSameAsStaff_forbidden() {
    SupportTicketEntity t = openTicket();
    when(ticketRepository.findById(t.getId())).thenReturn(Optional.of(t));

    BusinessException ex =
        assertThrows(BusinessException.class, () -> service.claim(t.getId(), userId));
    assertEquals("SELF_SERVICE_FORBIDDEN", ex.getCode());
  }

  @Test
  void resolve_inProgressTicket_publishesEvents() {
    SupportTicketEntity t = claimedTicket();
    when(ticketRepository.findById(t.getId())).thenReturn(Optional.of(t));
    when(ticketRepository.save(any(SupportTicketEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    var res =
        service.resolve(t.getId(), staffId, new ResolveTicketRequest("Fixed issue"));

    assertEquals("RESOLVED", res.status());
    assertEquals("Fixed issue", res.resolutionNote());
    verify(opsAlertPublisher).supportTicketResolved(any(SupportTicketEntity.class));
    verify(customerNotifyPublisher).supportTicketResolved(any(SupportTicketEntity.class));
  }

  @Test
  void reject_requiresReason() {
    SupportTicketEntity t = claimedTicket();
    when(ticketRepository.findById(t.getId())).thenReturn(Optional.of(t));

    BusinessException ex =
        assertThrows(
            BusinessException.class,
            () -> service.reject(t.getId(), staffId, new RejectTicketRequest("   ")));
    assertEquals("INVALID_REQUEST", ex.getCode());
  }

  @Test
  void requestInfo_setsStatusWaitingCustomer() {
    SupportTicketEntity t = openTicket();
    when(ticketRepository.findById(t.getId())).thenReturn(Optional.of(t));
    when(ticketRepository.save(any(SupportTicketEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    var res =
        service.requestInfo(t.getId(), staffId, new RequestInfoRequest("Vui long gui CCCD"));
    assertEquals("WAITING_CUSTOMER", res.status());
    assertEquals(staffId.toString(), res.assignedTo());
    verify(messageRepository).save(any(SupportTicketMessageEntity.class));
    verify(customerNotifyPublisher).supportTicketNeedInfo(any(SupportTicketEntity.class), any());
  }

  @Test
  void customerReply_fromWaiting_reopensInProgress() {
    SupportTicketEntity t = openTicket();
    t.setStatus("WAITING_CUSTOMER");
    t.setAssignedTo(staffId);
    when(ticketRepository.findByIdAndUserId(t.getId(), userId)).thenReturn(Optional.of(t));
    when(ticketRepository.save(any(SupportTicketEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    var res = service.customerReply(t.getId(), userId, new PostMessageRequest("Day la CCCD"));
    assertEquals("IN_PROGRESS", res.status());
    verify(messageRepository).save(any(SupportTicketMessageEntity.class));
  }

  @Test
  void customerReply_closedTicketConflicts() {
    SupportTicketEntity t = openTicket();
    t.setStatus("RESOLVED");
    when(ticketRepository.findByIdAndUserId(t.getId(), userId)).thenReturn(Optional.of(t));
    BusinessException ex =
        assertThrows(
            BusinessException.class,
            () -> service.customerReply(t.getId(), userId, new PostMessageRequest("late")));
    assertEquals("TICKET_CLOSED", ex.getCode());
    verify(messageRepository, never()).save(any());
  }

  @Test
  void resolve_fromWaitingCustomer_ok() {
    SupportTicketEntity t = openTicket();
    t.setStatus("WAITING_CUSTOMER");
    t.setAssignedTo(staffId);
    when(ticketRepository.findById(t.getId())).thenReturn(Optional.of(t));
    when(ticketRepository.save(any(SupportTicketEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    var res = service.resolve(t.getId(), staffId, new ResolveTicketRequest("ok without reply"));
    assertEquals("RESOLVED", res.status());
    verify(customerNotifyPublisher).supportTicketResolved(any(SupportTicketEntity.class));
  }

  private CustomerEntity sampleCustomer() {
    CustomerEntity c = new CustomerEntity();
    c.setId(userId);
    c.setEmail("alice@example.com");
    return c;
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
