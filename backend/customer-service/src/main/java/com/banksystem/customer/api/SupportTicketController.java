package com.banksystem.customer.api;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.api.PageResponse;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.common.security.UserContext;
import com.banksystem.customer.api.dto.SupportTicketDtos.CreateSupportTicketRequest;
import com.banksystem.customer.api.dto.SupportTicketDtos.MyTicketFilterRequest;
import com.banksystem.customer.api.dto.SupportTicketDtos.PostMessageRequest;
import com.banksystem.customer.api.dto.SupportTicketDtos.SupportTicketResponse;
import com.banksystem.customer.application.command.SupportTicketCommandService;
import com.banksystem.customer.application.query.SupportTicketQueryService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class SupportTicketController {

  private final SupportTicketQueryService queryService;
  private final SupportTicketCommandService commandService;

  public SupportTicketController(
      SupportTicketQueryService queryService,
      SupportTicketCommandService commandService) {
    this.queryService = queryService;
    this.commandService = commandService;
  }

  @PostMapping("/customers/me/support-tickets")
  @RequirePermission("ib:support:create")
  public ResponseEntity<ApiResponse<SupportTicketResponse>> create(
      @Valid @RequestBody CreateSupportTicketRequest req) {
    var user = UserContext.requireUser();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok(commandService.create(user.userId(), req)));
  }

  @GetMapping("/customers/me/support-tickets")
  @RequirePermission("ib:support:view")
  public ApiResponse<PageResponse<SupportTicketResponse>> listMine(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.ok(queryService.listMine(UserContext.requireUser().userId(), page, size));
  }

  @PostMapping("/customers/me/support-tickets/search")
  @RequirePermission("ib:support:view")
  public ApiResponse<PageResponse<SupportTicketResponse>> searchMine(
      @Valid @RequestBody MyTicketFilterRequest req) {
    return ApiResponse.ok(queryService.listMine(UserContext.requireUser().userId(), req.page(), req.size()));
  }

  @GetMapping("/customers/me/support-tickets/{id}")
  @RequirePermission("ib:support:view")
  public ApiResponse<SupportTicketResponse> getMine(@PathVariable UUID id) {
    return ApiResponse.ok(queryService.getMine(UserContext.requireUser().userId(), id));
  }

  /** Customer reply / extra context on open ticket (WAITING_CUSTOMER → reopens for staff). */
  @PostMapping("/customers/me/support-tickets/{id}/messages")
  @RequirePermission("ib:support:view")
  public ApiResponse<SupportTicketResponse> postMessage(
      @PathVariable UUID id, @Valid @RequestBody PostMessageRequest req) {
    return ApiResponse.ok(commandService.customerReply(id, UserContext.requireUser().userId(), req));
  }
}
