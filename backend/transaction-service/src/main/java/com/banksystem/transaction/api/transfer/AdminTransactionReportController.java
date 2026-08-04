package com.banksystem.transaction.api.transfer;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.transaction.api.dto.ReportDtos.TransactionReportFilterRequest;
import com.banksystem.transaction.api.dto.ReportDtos.TransactionReportResponse;
import com.banksystem.transaction.application.transfer.TransactionReportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
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
  public ApiResponse<TransactionReportResponse> report(@Valid @ModelAttribute TransactionReportFilterRequest req) {
    return ApiResponse.ok(service.report(req));
  }

  /**
   * Stream CSV export of transaction report records using MyBatis Cursor & Server-side Cursor.
   */
  @GetMapping("/export-csv")
  public ResponseEntity<StreamingResponseBody> exportCsv(@Valid @ModelAttribute TransactionReportFilterRequest req) {
    StreamingResponseBody body = outputStream -> service.exportCsvStream(req, outputStream);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"transaction_report.csv\"")
        .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
        .body(body);
  }
}
