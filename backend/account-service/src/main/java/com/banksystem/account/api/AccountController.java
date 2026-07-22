package com.banksystem.account.api;

import com.banksystem.account.api.dto.AccountDtos.AccountResponse;
import com.banksystem.account.api.dto.AccountDtos.LedgerEntryResponse;
import com.banksystem.account.api.dto.AccountDtos.OpenAccountRequest;
import com.banksystem.account.api.dto.AccountDtos.TopUpRequest;
import com.banksystem.account.api.dto.AccountDtos.TopUpResponse;
import com.banksystem.account.application.CustomerAccountService;
import com.banksystem.account.application.query.LedgerStatementQuery;
import com.banksystem.common.security.UserContext;
import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.api.PageResponse;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer-facing account operations.
 * No admin endpoints and no business rules here — only HTTP adaptation.
 */
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

  private final CustomerAccountService service;

  public AccountController(CustomerAccountService service) {
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

  /**
   * Account ledger statement (DEBIT/CREDIT lines). Ownership enforced in application service.
   * Query params: page, size, entryType=DEBIT|CREDIT, from, to (ISO-8601 instants).
   */
  @GetMapping("/{id}/statement")
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

  /**
   * Download account ledger as CSV (same filters as statement; max
   * {@link LedgerStatementQuery#MAX_EXPORT_ROWS} rows).
   */
  @GetMapping(value = "/{id}/statement/export.csv", produces = "text/csv")
  public ResponseEntity<byte[]> exportStatementCsv(
      @PathVariable UUID id,
      @RequestParam(required = false) String entryType,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
    LedgerStatementQuery query =
        LedgerStatementQuery.of(id, 0, LedgerStatementQuery.MAX_EXPORT_ROWS, entryType, from, to);
    byte[] csv = service.exportStatementCsv(query, UserContext.requireUser());
    String filename = "statement-" + id + ".csv";
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
        .body(csv);
  }
}
