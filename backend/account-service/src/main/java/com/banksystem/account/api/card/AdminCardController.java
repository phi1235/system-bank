package com.banksystem.account.api.card;
import com.banksystem.account.application.account.*;
import com.banksystem.account.application.card.*;
import com.banksystem.account.application.deposit.*;
import com.banksystem.account.application.ledger.*;
import com.banksystem.account.domain.account.*;
import com.banksystem.account.domain.card.*;
import com.banksystem.account.domain.deposit.*;
import com.banksystem.account.domain.ledger.*;
import com.banksystem.account.api.dto.*;

import com.banksystem.account.api.dto.CardDtos.AdminCardFilterRequest;
import com.banksystem.account.api.dto.CardDtos.AdminCardRow;
import com.banksystem.account.api.dto.CardDtos.BatchApproveRequest;
import com.banksystem.account.api.dto.CardDtos.BatchApproveResult;
import com.banksystem.account.api.dto.CardDtos.CardResponse;
import com.banksystem.account.api.dto.CardDtos.RejectCardRequest;
import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.api.PageResponse;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.common.security.UserContext;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
  public ApiResponse<PageResponse<AdminCardRow>> list(@Valid @ModelAttribute AdminCardFilterRequest req) {
    return ApiResponse.ok(service.queue(req));
  }

  @PostMapping("/findCardByCondition")
  @RequirePermission("accounts:lookup:view")
  public ApiResponse<PageResponse<AdminCardRow>> findCardByCondition(@Valid @RequestBody AdminCardFilterRequest req) {
    return ApiResponse.ok(service.queue(req));
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
