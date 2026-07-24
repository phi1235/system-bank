package com.banksystem.customer.api;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.api.PageResponse;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.common.security.UserContext;
import com.banksystem.customer.api.dto.SupportTicketDtos.PostMessageRequest;
import com.banksystem.customer.api.dto.SupportTicketDtos.RejectTicketRequest;
import com.banksystem.customer.api.dto.SupportTicketDtos.RequestInfoRequest;
import com.banksystem.customer.api.dto.SupportTicketDtos.ResolveTicketRequest;
import com.banksystem.customer.api.dto.SupportTicketDtos.SupportTicketResponse;
import com.banksystem.customer.application.SupportTicketService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/support-tickets")
public class AdminSupportTicketController {

  private final SupportTicketService service;

  public AdminSupportTicketController(SupportTicketService service) {
    this.service = service;
  }

  @GetMapping
  @RequirePermission("support:tickets:list")
  public ApiResponse<PageResponse<SupportTicketResponse>> list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String q) {
    return ApiResponse.ok(service.adminList(status, category, q, page, size));
  }

  @GetMapping("/{id}")
  @RequirePermission("support:tickets:list")
  public ApiResponse<SupportTicketResponse> get(@PathVariable UUID id) {
    return ApiResponse.ok(service.adminGet(id));
  }

  @PostMapping("/{id}/claim")
  @RequirePermission("support:tickets:claim")
  public ApiResponse<SupportTicketResponse> claim(@PathVariable UUID id) {
    return ApiResponse.ok(service.claim(id, UserContext.requireUser().userId()));
  }

  @PostMapping("/{id}/resolve")
  @RequirePermission("support:tickets:decide")
  public ApiResponse<SupportTicketResponse> resolve(
      @PathVariable UUID id, @RequestBody(required = false) ResolveTicketRequest req) {
    return ApiResponse.ok(
        service.resolve(id, UserContext.requireUser().userId(), req == null ? new ResolveTicketRequest(null) : req));
  }

  @PostMapping("/{id}/reject")
  @RequirePermission("support:tickets:decide")
  public ApiResponse<SupportTicketResponse> reject(
      @PathVariable UUID id, @Valid @RequestBody RejectTicketRequest req) {
    return ApiResponse.ok(service.reject(id, UserContext.requireUser().userId(), req));
  }

  /** Staff requests more info → WAITING_CUSTOMER + thread message + customer notify. */
  @PostMapping("/{id}/request-info")
  @RequirePermission("support:tickets:decide")
  public ApiResponse<SupportTicketResponse> requestInfo(
      @PathVariable UUID id, @Valid @RequestBody RequestInfoRequest req) {
    return ApiResponse.ok(service.requestInfo(id, UserContext.requireUser().userId(), req));
  }

  /** Staff posts a message on open ticket without closing. */
  @PostMapping("/{id}/messages")
  @RequirePermission("support:tickets:decide")
  public ApiResponse<SupportTicketResponse> postMessage(
      @PathVariable UUID id, @Valid @RequestBody PostMessageRequest req) {
    return ApiResponse.ok(service.staffMessage(id, UserContext.requireUser().userId(), req));
  }
}
