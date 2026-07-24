package com.banksystem.customer.api;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.api.PageResponse;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.common.security.UserContext;
import com.banksystem.customer.api.dto.SupportTicketDtos.CreateSupportTicketRequest;
import com.banksystem.customer.api.dto.SupportTicketDtos.SupportTicketResponse;
import com.banksystem.customer.application.SupportTicketService;
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

  private final SupportTicketService service;

  public SupportTicketController(SupportTicketService service) {
    this.service = service;
  }

  @PostMapping("/customers/me/support-tickets")
  @RequirePermission("ib:support:create")
  public ResponseEntity<ApiResponse<SupportTicketResponse>> create(
      @Valid @RequestBody CreateSupportTicketRequest req) {
    var user = UserContext.requireUser();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok(service.create(user.userId(), req)));
  }

  @GetMapping("/customers/me/support-tickets")
  @RequirePermission("ib:support:view")
  public ApiResponse<PageResponse<SupportTicketResponse>> listMine(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.ok(service.listMine(UserContext.requireUser().userId(), page, size));
  }

  @GetMapping("/customers/me/support-tickets/{id}")
  @RequirePermission("ib:support:view")
  public ApiResponse<SupportTicketResponse> getMine(@PathVariable UUID id) {
    return ApiResponse.ok(service.getMine(UserContext.requireUser().userId(), id));
  }
}
