package com.banksystem.customer.application.mapper;
import com.banksystem.customer.application.customer.*;
import com.banksystem.customer.application.support.*;
import com.banksystem.customer.application.dashboard.*;
import com.banksystem.customer.domain.customer.*;
import com.banksystem.customer.domain.support.*;
import com.banksystem.customer.api.dto.*;

import com.banksystem.customer.api.dto.SupportTicketDtos.SupportTicketMessageResponse;
import com.banksystem.customer.api.dto.SupportTicketDtos.SupportTicketResponse;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SupportTicketMapper {

  public SupportTicketResponse toResponse(SupportTicketEntity t, List<SupportTicketMessageEntity> messages) {
    List<SupportTicketMessageResponse> messageDtos = messages == null ? List.of() : messages.stream()
        .map(this::toMessageResponse)
        .toList();

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
        messageDtos);
  }

  public SupportTicketMessageResponse toMessageResponse(SupportTicketMessageEntity m) {
    return new SupportTicketMessageResponse(
        m.getId().toString(),
        m.getTicketId().toString(),
        m.getAuthorUserId().toString(),
        m.getAuthorRole(),
        m.getBody(),
        m.getCreatedAt());
  }
}
