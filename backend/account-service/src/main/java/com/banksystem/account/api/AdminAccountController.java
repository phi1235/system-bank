package com.banksystem.account.api;

import com.banksystem.account.api.dto.AccountDtos.AccountResponse;
import com.banksystem.account.api.dto.AccountDtos.TopUpRequest;
import com.banksystem.account.api.dto.AccountDtos.TopUpResponse;
import com.banksystem.account.application.AdminAccountService;
import com.banksystem.account.application.query.AdminAccountSearchQuery;
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
 * Staff-facing account operations.
 * Controller responsibility: HTTP mapping + auth gate only.
 * Business rules live in {@link AdminAccountService}.
 */
@RestController
@RequestMapping("/api/v1/admin/accounts")
public class AdminAccountController {

  private final AdminAccountService service;

  public AdminAccountController(AdminAccountService service) {
    this.service = service;
  }

  @GetMapping
  @RequirePermission("accounts:lookup:view")
  public ApiResponse<?> list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String accountType,
      @RequestParam(required = false, defaultValue = "false") boolean noCount) {
    AdminAccountSearchQuery query = AdminAccountSearchQuery.of(q, status, accountType, page, size);
    if (noCount) {
      return ApiResponse.ok(service.adminListSlice(query));
    }
    return ApiResponse.ok(service.adminList(query));
  }

  @GetMapping("/{id}")
  @RequirePermission("accounts:lookup:view")
  public ApiResponse<AccountResponse> get(@PathVariable UUID id) {
    return ApiResponse.ok(service.get(id));
  }

  @PostMapping("/{id}/freeze")
  @RequirePermission("accounts:freeze:execute")
  public ApiResponse<AccountResponse> freeze(@PathVariable UUID id) {
    return ApiResponse.ok(service.freeze(id, UserContext.requireUser()));
  }

  @PostMapping("/{id}/unfreeze")
  @RequirePermission("accounts:freeze:execute")
  public ApiResponse<AccountResponse> unfreeze(@PathVariable UUID id) {
    return ApiResponse.ok(service.unfreeze(id, UserContext.requireUser()));
  }

  @PostMapping("/{id}/top-up")
  @RequirePermission("accounts:topup:execute")
  public ApiResponse<TopUpResponse> topUp(
      @PathVariable UUID id,
      @Valid @RequestBody TopUpRequest req) {
    return ApiResponse.ok(service.topUp(id, req, UserContext.requireUser()));
  }
}
