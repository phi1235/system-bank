package com.banksystem.transaction.api;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.api.PageResponse;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.common.security.UserContext;
import com.banksystem.transaction.api.dto.TransferDtos.TransferDetailResponse;
import com.banksystem.transaction.api.dto.TransferDtos.TransferQuoteResponse;
import com.banksystem.transaction.api.dto.TransferDtos.TransferRequest;
import com.banksystem.transaction.api.dto.TransferDtos.TransferResponse;
import com.banksystem.transaction.application.TransferService;
import com.banksystem.transaction.application.query.AdminTransferListQuery;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class TransferController {

  private final TransferService transferService;

  public TransferController(TransferService transferService) {
    this.transferService = transferService;
  }

  @PostMapping("/transactions/transfers")
  public ApiResponse<TransferResponse> transfer(
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody TransferRequest req,
      HttpServletRequest http) {
    return ApiResponse.ok(transferService.transfer(
        UserContext.requireUser(), idempotencyKey, req, clientIp(http)));
  }

  /**
   * Fee + daily-limit remaining preview before submit.
   * amount optional: when omitted/0 only limits are returned (fee=0).
   */
  @GetMapping("/transactions/transfers/quote")
  public ApiResponse<TransferQuoteResponse> quote(
      @RequestParam(required = false) BigDecimal amount) {
    return ApiResponse.ok(transferService.quote(UserContext.requireUser().userId(), amount));
  }

  @GetMapping("/transactions/transfers")
  public ApiResponse<PageResponse<TransferResponse>> myTransfers(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
    return ApiResponse.ok(transferService.myHistory(
        UserContext.requireUser().userId(),
        page,
        Math.min(size, 100),
        status,
        from,
        to));
  }

  @GetMapping("/transactions/transfers/{id}")
  public ApiResponse<TransferResponse> get(@PathVariable UUID id) {
    return ApiResponse.ok(transferService.get(id, UserContext.requireUser()));
  }

  /** Transfer + saga step timeline (owner or staff). */
  @GetMapping("/transactions/transfers/{id}/detail")
  public ApiResponse<TransferDetailResponse> detail(@PathVariable UUID id) {
    return ApiResponse.ok(transferService.getDetail(id, UserContext.requireUser()));
  }

  @GetMapping({"/admin/transfers", "/transactions/admin/transfers"})
  @RequirePermission("transactions:list:view")
  public ApiResponse<?> adminTransfers(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String transferId,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false, defaultValue = "false") boolean noCount,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant lastCreatedAt) {
    var query = AdminTransferListQuery.of(
        status, transferId, q, from, to, page, size, lastCreatedAt);
    if (noCount) {
      return ApiResponse.ok(transferService.adminListSlice(query));
    }
    return ApiResponse.ok(transferService.adminList(query));
  }

  private String clientIp(HttpServletRequest request) {
    String xff = request.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
      return xff.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
