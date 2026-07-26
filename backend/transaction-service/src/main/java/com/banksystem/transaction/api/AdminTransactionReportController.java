package com.banksystem.transaction.api;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.transaction.api.dto.ReportDtos.TransactionReportResponse;
import com.banksystem.transaction.application.TransactionReportService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff transaction report dashboard. HTTP + permission only; rules in
 * {@link TransactionReportService}. Gateway: {@code /api/v1/admin/**} → TRANSACTION-SERVICE.
 */
@RestController
@RequestMapping("/api/v1/admin/transactions/reports")
@RequirePermission("transactions:report:view")
public class AdminTransactionReportController {

  private final TransactionReportService service;

  public AdminTransactionReportController(TransactionReportService service) {
    this.service = service;
  }

  /**
   * Aggregated transfer report over banking days. Defaults: last 30 days ending today.
   * {@code accountId} narrows to one source account; {@code top} caps the account ranking.
   */
  @GetMapping
  public ApiResponse<TransactionReportResponse> report(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) String accountId,
      @RequestParam(required = false) Integer top) {
    return ApiResponse.ok(service.report(from, to, accountId, top));
  }
}
