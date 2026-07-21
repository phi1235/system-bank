package com.banksystem.account.api;

import com.banksystem.account.api.dto.AccountDtos.AccountResponse;
import com.banksystem.account.application.AdminAccountService;
import com.banksystem.account.application.query.AdminAccountSearchQuery;
import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.api.PageResponse;
import com.banksystem.common.security.RequirePermission;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
  public ApiResponse<PageResponse<AccountResponse>> list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String status) {
    AdminAccountSearchQuery query = AdminAccountSearchQuery.of(q, status, page, size);
    return ApiResponse.ok(service.adminList(query));
  }

  @PostMapping("/{id}/freeze")
  @RequirePermission("accounts:freeze:execute")
  public ApiResponse<AccountResponse> freeze(@PathVariable UUID id) {
    return ApiResponse.ok(service.freeze(id));
  }

  @PostMapping("/{id}/unfreeze")
  @RequirePermission("accounts:freeze:execute")
  public ApiResponse<AccountResponse> unfreeze(@PathVariable UUID id) {
    return ApiResponse.ok(service.unfreeze(id));
  }
}
