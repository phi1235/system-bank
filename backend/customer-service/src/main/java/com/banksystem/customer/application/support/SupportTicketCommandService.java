package com.banksystem.customer.application.support;

import com.banksystem.customer.api.dto.SupportTicketDtos.*;
import java.util.UUID;

public interface SupportTicketCommandService {
  SupportTicketResponse create(UUID userId, CreateSupportTicketRequest req);
  SupportTicketResponse claim(UUID ticketId, UUID staffId);
  SupportTicketResponse resolve(UUID ticketId, UUID staffId, ResolveTicketRequest req);
  SupportTicketResponse reject(UUID ticketId, UUID staffId, RejectTicketRequest req);
  SupportTicketResponse requestInfo(UUID ticketId, UUID staffId, RequestInfoRequest req);
  SupportTicketResponse customerReply(UUID ticketId, UUID userId, PostMessageRequest req);
  SupportTicketResponse staffMessage(UUID ticketId, UUID staffId, PostMessageRequest req);
}
