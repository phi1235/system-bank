package com.banksystem.transaction.api;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.transaction.api.dto.ReportDtos.TransactionReportResponse;
import com.banksystem.transaction.application.TransactionReportService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

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

  /**
   * Stream CSV export of transaction report records using MyBatis Cursor & Server-side Cursor.
   */
  @GetMapping("/export-csv")
  public ResponseEntity<StreamingResponseBody> exportCsv(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) String accountId) {
    StreamingResponseBody body = outputStream -> service.exportCsvStream(from, to, accountId, outputStream);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"transaction_report.csv\"")
        .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
        .body(body);
  }
}
