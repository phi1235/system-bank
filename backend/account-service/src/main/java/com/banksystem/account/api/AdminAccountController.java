package com.banksystem.account.api;

import com.banksystem.account.api.dto.AccountDtos.AccountResponse;
import com.banksystem.account.application.AccountAppService;
import com.banksystem.account.application.query.AdminAccountSearchQuery;
import com.banksystem.account.config.UserContext;
import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.api.PageResponse;
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
 * Business rules live in {@link AccountAppService}.
 */
@RestController
@RequestMapping("/api/v1/admin/accounts")
public class AdminAccountController {

  private final AccountAppService service;

  public AdminAccountController(AccountAppService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<PageResponse<AccountResponse>> list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String status) {
    UserContext.requirePermission("accounts:lookup:view");
    AdminAccountSearchQuery query = AdminAccountSearchQuery.of(q, status, page, size);
    return ApiResponse.ok(service.adminList(query));
  }

  @PostMapping("/{id}/freeze")
  public ApiResponse<AccountResponse> freeze(@PathVariable UUID id) {
    UserContext.requirePermission("accounts:freeze:execute");
    return ApiResponse.ok(service.freeze(id));
  }

  @PostMapping("/{id}/unfreeze")
  public ApiResponse<AccountResponse> unfreeze(@PathVariable UUID id) {
    UserContext.requirePermission("accounts:freeze:execute");
    return ApiResponse.ok(service.unfreeze(id));
  }
}
