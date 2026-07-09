package com.banksystem.account.api;

import com.banksystem.account.api.dto.AccountDtos.AccountResponse;
import com.banksystem.account.api.dto.AccountDtos.OpenAccountRequest;
import com.banksystem.account.application.AccountAppService;
import com.banksystem.account.config.UserContext;
import com.banksystem.common.api.ApiResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AccountController {

  private final AccountAppService service;

  public AccountController(AccountAppService service) {
    this.service = service;
  }

  @PostMapping("/accounts")
  public ResponseEntity<ApiResponse<AccountResponse>> open(@RequestBody(required = false) OpenAccountRequest req) {
    var user = UserContext.requireUser();
    OpenAccountRequest body = req == null ? new OpenAccountRequest("PAYMENT") : req;
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.ok(service.open(user.userId(), body)));
  }

  @GetMapping("/accounts")
  public ApiResponse<List<AccountResponse>> list() {
    return ApiResponse.ok(service.listMine(UserContext.requireUser().userId()));
  }

  @GetMapping("/accounts/{id}")
  public ApiResponse<AccountResponse> get(@PathVariable UUID id) {
    return ApiResponse.ok(service.get(id, UserContext.requireUser()));
  }

  @PostMapping({"/accounts/{id}/freeze", "/admin/accounts/{id}/freeze"})
  public ApiResponse<AccountResponse> freeze(@PathVariable UUID id) {
    UserContext.requireAdmin();
    return ApiResponse.ok(service.freeze(id));
  }

  @PostMapping({"/accounts/{id}/unfreeze", "/admin/accounts/{id}/unfreeze"})
  public ApiResponse<AccountResponse> unfreeze(@PathVariable UUID id) {
    UserContext.requireAdmin();
    return ApiResponse.ok(service.unfreeze(id));
  }
}
