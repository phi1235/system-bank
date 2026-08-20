package com.banksystem.corporate.api;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.common.security.GatewayUser;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.common.security.UserContext;
import com.banksystem.corporate.api.dto.PayoutBatchDtos.BatchProgressResponse;
import com.banksystem.corporate.api.dto.PayoutBatchDtos.BatchSummaryResponse;
import com.banksystem.corporate.api.dto.PayoutBatchDtos.BatchValidationSummaryResponse;
import com.banksystem.corporate.api.dto.PayoutBatchDtos.CreateBatchRequest;
import com.banksystem.corporate.api.dto.PayoutBatchDtos.CancelBatchRequest;
import com.banksystem.corporate.api.dto.PayoutBatchDtos.PayoutItemResponse;
import com.banksystem.corporate.api.dto.PayoutBatchDtos.PayoutPageRequest;
import com.banksystem.corporate.application.payout.ExcelIngestionService;
import com.banksystem.corporate.application.payout.PayoutBatchListQuery;
import com.banksystem.corporate.application.payout.PayoutBatchService;
import com.banksystem.corporate.application.payout.PayoutItemListQuery;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/corporations/{corporateId}/payout-batches")
public class PayoutBatchController {

  private final PayoutBatchService batchService;
  private final ExcelIngestionService ingestionService;

  public PayoutBatchController(
      PayoutBatchService batchService,
      ExcelIngestionService ingestionService) {
    this.batchService = batchService;
    this.ingestionService = ingestionService;
  }

  @PostMapping
  @RequirePermission("corp:portal:view")
  public ResponseEntity<ApiResponse<BatchSummaryResponse>> createBatch(
      @PathVariable("corporateId") UUID corporateId,
      @Valid @RequestBody CreateBatchRequest req) {
    GatewayUser user = UserContext.requireUser();
    BatchSummaryResponse res = batchService.createBatch(corporateId, user.userId(), req);
    return ResponseEntity.ok(ApiResponse.ok(res));
  }

  @GetMapping
  @RequirePermission("corp:portal:view")
  public ResponseEntity<ApiResponse<Page<BatchSummaryResponse>>> listBatches(
      @PathVariable("corporateId") UUID corporateId,
      @Valid @ModelAttribute PayoutPageRequest request) {
    GatewayUser user = UserContext.requireUser();
    PayoutBatchListQuery query = PayoutBatchListQuery.of(request);
    Page<BatchSummaryResponse> res = batchService.listBatches(
        corporateId, user.userId(), query.pageable());
    return ResponseEntity.ok(ApiResponse.ok(res));
  }

  @GetMapping("/{batchId}")
  @RequirePermission("corp:portal:view")
  public ResponseEntity<ApiResponse<BatchSummaryResponse>> getBatch(
      @PathVariable("corporateId") UUID corporateId,
      @PathVariable("batchId") UUID batchId) {
    GatewayUser user = UserContext.requireUser();
    BatchSummaryResponse res = batchService.getBatch(corporateId, batchId, user.userId());
    return ResponseEntity.ok(ApiResponse.ok(res));
  }

  @PostMapping(value = "/{batchId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @RequirePermission("corp:portal:view")
  public ResponseEntity<ApiResponse<BatchSummaryResponse>> uploadExcel(
      @PathVariable("corporateId") UUID corporateId,
      @PathVariable("batchId") UUID batchId,
      @RequestParam("file") MultipartFile file) {
    GatewayUser user = UserContext.requireUser();
    batchService.requireMakerBatch(corporateId, batchId, user.userId());
    try {
      BatchSummaryResponse res = ingestionService.ingestExcel(
          corporateId, batchId, file.getInputStream(), file.getOriginalFilename());
      return ResponseEntity.ok(ApiResponse.ok(res));
    } catch (IOException e) {
      throw new BusinessException("FILE_READ_ERROR", "Failed to read uploaded Excel file: " + e.getMessage());
    }
  }

  @GetMapping("/template")
  @RequirePermission("corp:portal:view")
  public ResponseEntity<byte[]> downloadTemplate(
      @PathVariable("corporateId") UUID corporateId) {
    byte[] excelBytes = ingestionService.generateTemplateExcel();
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=mau_chi_tra_luong.xlsx")
        .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .body(excelBytes);
  }

  @GetMapping("/{batchId}/error-report")
  @RequirePermission("corp:portal:view")
  public ResponseEntity<byte[]> downloadErrorReport(
      @PathVariable("corporateId") UUID corporateId,
      @PathVariable("batchId") UUID batchId) {
    GatewayUser user = UserContext.requireUser();
    batchService.getBatch(corporateId, batchId, user.userId());
    byte[] excelBytes = ingestionService.downloadErrorReport(batchId);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=danh_sach_dong_loi_" + batchId + ".xlsx")
        .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .body(excelBytes);
  }

  @PostMapping("/{batchId}/submit")
  @RequirePermission("corp:portal:view")
  public ResponseEntity<ApiResponse<BatchSummaryResponse>> submitBatch(
      @PathVariable("corporateId") UUID corporateId,
      @PathVariable("batchId") UUID batchId) {
    GatewayUser user = UserContext.requireUser();
    BatchSummaryResponse res = batchService.submitBatch(corporateId, batchId, user.userId());
    return ResponseEntity.ok(ApiResponse.ok(res));
  }

  @PostMapping("/{batchId}/cancel")
  @RequirePermission("corp:portal:view")
  public ResponseEntity<ApiResponse<BatchSummaryResponse>> cancelBatch(
      @PathVariable("corporateId") UUID corporateId,
      @PathVariable("batchId") UUID batchId,
      @Valid @RequestBody CancelBatchRequest request) {
    GatewayUser user = UserContext.requireUser();
    BatchSummaryResponse res = batchService.cancelBatch(
        corporateId, batchId, user.userId(), request.reason());
    return ResponseEntity.ok(ApiResponse.ok(res));
  }

  @PostMapping("/{batchId}/retry")
  @RequirePermission("corp:portal:view")
  public ResponseEntity<ApiResponse<BatchSummaryResponse>> retryBatch(
      @PathVariable("corporateId") UUID corporateId,
      @PathVariable("batchId") UUID batchId) {
    GatewayUser user = UserContext.requireUser();
    BatchSummaryResponse res = batchService.retryFailedItems(corporateId, batchId, user.userId());
    return ResponseEntity.ok(ApiResponse.ok(res));
  }

  @GetMapping("/{batchId}/items")
  @RequirePermission("corp:portal:view")
  public ResponseEntity<ApiResponse<Page<PayoutItemResponse>>> listItems(
      @PathVariable("corporateId") UUID corporateId,
      @PathVariable("batchId") UUID batchId,
      @Valid @ModelAttribute PayoutPageRequest request) {
    GatewayUser user = UserContext.requireUser();
    PayoutItemListQuery query = PayoutItemListQuery.of(request);
    Page<PayoutItemResponse> res = batchService.listBatchItems(
        corporateId, batchId, user.userId(), query.pageable());
    return ResponseEntity.ok(ApiResponse.ok(res));
  }

  @GetMapping("/{batchId}/validation-summary")
  @RequirePermission("corp:portal:view")
  public ResponseEntity<ApiResponse<BatchValidationSummaryResponse>> getValidationSummary(
      @PathVariable("corporateId") UUID corporateId,
      @PathVariable("batchId") UUID batchId) {
    GatewayUser user = UserContext.requireUser();
    BatchValidationSummaryResponse res = batchService.getValidationSummary(corporateId, batchId, user.userId());
    return ResponseEntity.ok(ApiResponse.ok(res));
  }

  @GetMapping("/{batchId}/progress")
  @RequirePermission("corp:portal:view")
  public ResponseEntity<ApiResponse<BatchProgressResponse>> getProgress(
      @PathVariable("corporateId") UUID corporateId,
      @PathVariable("batchId") UUID batchId) {
    GatewayUser user = UserContext.requireUser();
    BatchProgressResponse res = batchService.getProgress(corporateId, batchId, user.userId());
    return ResponseEntity.ok(ApiResponse.ok(res));
  }
}
