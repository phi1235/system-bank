package com.banksystem.auth.application.auth;
import static com.banksystem.auth.api.dto.AuthDtos.*;
import static com.banksystem.auth.api.dto.PasswordResetDtos.*;
import static com.banksystem.auth.api.dto.RbacDtos.*;

import com.banksystem.common.api.PageResponse;
import com.banksystem.auth.api.dto.PasswordResetDtos.*;
import java.util.UUID;

public interface PasswordResetService {
  TicketResponse createTicket(CreateTicketRequest req);
  PageResponse<TicketResponse> listTickets(String status, Integer page, Integer size);
  FulfillResponse fulfill(UUID ticketId, UUID adminId);
  FulfillResponse resetByUserId(UUID userId, UUID adminId, String channel);
  TicketResponse reject(UUID ticketId, UUID adminId, RejectRequest req);
  void lockUser(UUID targetUserId, UUID adminId, LockRequest req);
  void unlockUser(UUID targetUserId, UUID adminId);
  void changePassword(UUID userId, ChangePasswordRequest req);
}
