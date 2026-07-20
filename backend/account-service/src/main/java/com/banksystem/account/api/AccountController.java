package com.banksystem.account.api;

import com.banksystem.account.api.dto.AccountDtos.AccountResponse;
import com.banksystem.account.api.dto.AccountDtos.LedgerEntryResponse;
import com.banksystem.account.api.dto.AccountDtos.OpenAccountRequest;
import com.banksystem.account.application.AccountAppService;
import com.banksystem.account.application.query.LedgerStatementQuery;
import com.banksystem.account.config.UserContext;
import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.api.PageResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
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

  /**
   * Account ledger statement (DEBIT/CREDIT lines). Ownership enforced in application service.
   * Query params: page, size, entryType=DEBIT|CREDIT, from, to (ISO-8601 instants).
   */
  @GetMapping("/accounts/{id}/statement")
  public ApiResponse<PageResponse<LedgerEntryResponse>> statement(
      @PathVariable UUID id,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String entryType,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
    LedgerStatementQuery query = LedgerStatementQuery.of(id, page, size, entryType, from, to);
    return ApiResponse.ok(service.statement(query, UserContext.requireUser()));
  }

  @PostMapping({"/accounts/{id}/freeze", "/admin/accounts/{id}/freeze"})
  public ApiResponse<AccountResponse> freeze(@PathVariable UUID id) {
    UserContext.requirePermission("accounts:freeze:execute");
    return ApiResponse.ok(service.freeze(id));
  }

  @PostMapping({"/accounts/{id}/unfreeze", "/admin/accounts/{id}/unfreeze"})
  public ApiResponse<AccountResponse> unfreeze(@PathVariable UUID id) {
    UserContext.requirePermission("accounts:freeze:execute");
    return ApiResponse.ok(service.unfreeze(id));
  }
}
