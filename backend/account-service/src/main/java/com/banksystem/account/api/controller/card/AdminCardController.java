package com.banksystem.account.api.controller.card;

import com.banksystem.account.api.dto.card.CardDtos.AdminCardRow;
import com.banksystem.account.api.dto.card.CardDtos.BatchApproveRequest;
import com.banksystem.account.api.dto.card.CardDtos.BatchApproveResult;
import com.banksystem.account.api.dto.card.CardDtos.CardResponse;
import com.banksystem.account.api.dto.card.CardDtos.RejectCardRequest;
import com.banksystem.account.application.card.CardApprovalService;
import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.api.PageResponse;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.common.security.UserContext;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff card approval. HTTP + permission only; rules in {@link CardApprovalService}.
 * Gateway: {@code /api/v1/admin/cards/**} → ACCOUNT-SERVICE.
 */
@RestController
@RequestMapping("/api/v1/admin/cards")
public class AdminCardController {

  private final CardApprovalService service;

  public AdminCardController(CardApprovalService service) {
    this.service = service;
  }

  /** Approval queue (default REQUESTED, oldest first) or browse by status. */
  @GetMapping
  @RequirePermission("accounts:lookup:view")
  public ApiResponse<PageResponse<AdminCardRow>> list(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String q) {
    return ApiResponse.ok(service.queue(status, page, size, q));
  }

  @PostMapping("/{id}/approve")
  @RequirePermission("cards:approve:execute")
  public ApiResponse<CardResponse> approve(@PathVariable UUID id) {
    return ApiResponse.ok(service.approve(id, UserContext.requireUser().userId()));
  }

  @PostMapping("/batch-approve")
  @RequirePermission("cards:approve:execute")
  public ApiResponse<BatchApproveResult> batchApprove(@Valid @RequestBody BatchApproveRequest request) {
    return ApiResponse.ok(service.batchApprove(request.ids(), UserContext.requireUser().userId()));
  }

  @PostMapping("/{id}/reject")
  @RequirePermission("cards:approve:execute")
  public ApiResponse<CardResponse> reject(
      @PathVariable UUID id, @Valid @RequestBody RejectCardRequest request) {
    return ApiResponse.ok(service.reject(id, request.reason(), UserContext.requireUser().userId()));
  }
}
