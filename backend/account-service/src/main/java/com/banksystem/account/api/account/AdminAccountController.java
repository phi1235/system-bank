package com.banksystem.account.api.account;
import com.banksystem.account.application.account.*;
import com.banksystem.account.application.card.*;
import com.banksystem.account.application.deposit.*;
import com.banksystem.account.application.ledger.*;
import com.banksystem.account.domain.account.*;
import com.banksystem.account.domain.card.*;
import com.banksystem.account.domain.deposit.*;
import com.banksystem.account.domain.ledger.*;
import com.banksystem.account.api.dto.*;

import com.banksystem.account.api.dto.AccountDtos.AccountResponse;
import com.banksystem.account.api.dto.AccountDtos.AdminAccountFilterRequest;
import com.banksystem.account.api.dto.AccountDtos.TopUpRequest;
import com.banksystem.account.api.dto.AccountDtos.TopUpResponse;
import com.banksystem.common.api.ApiResponse;
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
  public ApiResponse<?> list(@Valid @ModelAttribute AdminAccountFilterRequest req) {
    return ApiResponse.ok(service.adminList(req));
  }

  @PostMapping("/findAccountByCondition")
  @RequirePermission("accounts:lookup:view")
  public ApiResponse<?> findAccountByCondition(@Valid @RequestBody AdminAccountFilterRequest req) {
    return ApiResponse.ok(service.adminList(req));
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
