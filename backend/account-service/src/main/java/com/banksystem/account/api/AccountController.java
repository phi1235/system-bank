package com.banksystem.account.api;

import com.banksystem.account.api.dto.AccountDtos.AccountResponse;
import com.banksystem.account.api.dto.AccountDtos.OpenAccountRequest;
import com.banksystem.account.application.AccountAppService;
import com.banksystem.account.config.UserContext;
import com.banksystem.common.api.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer-facing account operations.
 * No admin endpoints and no business rules here — only HTTP adaptation.
 */
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

  private final AccountAppService service;

  public AccountController(AccountAppService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<ApiResponse<AccountResponse>> open(
      @RequestBody(required = false) OpenAccountRequest req) {
    var user = UserContext.requireUser();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok(service.open(user.userId(), req)));
  }

  @GetMapping
  public ApiResponse<List<AccountResponse>> list() {
    return ApiResponse.ok(service.listMine(UserContext.requireUser().userId()));
  }

  @GetMapping("/{id}")
  public ApiResponse<AccountResponse> get(@PathVariable UUID id) {
    return ApiResponse.ok(service.get(id, UserContext.requireUser()));
  }
}
