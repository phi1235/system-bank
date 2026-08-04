package com.banksystem.customer.application.support;

import com.banksystem.common.api.PageResponse;
import com.banksystem.customer.api.dto.SupportTicketDtos.*;
import com.banksystem.customer.domain.support.SupportTicketEntity;
import java.util.UUID;

public interface SupportTicketQueryService {
  PageResponse<SupportTicketResponse> listMine(UUID userId, Integer page, Integer size);
  SupportTicketResponse getMine(UUID userId, UUID ticketId);
  PageResponse<SupportTicketResponse> adminList(AdminTicketFilterRequest req);
  PageResponse<SupportTicketResponse> adminList(TicketSearchQuery query);
  SupportTicketResponse adminGet(UUID ticketId);
  SupportTicketEntity require(UUID id);
}
