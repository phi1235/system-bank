package com.banksystem.account.api;

import com.banksystem.account.api.dto.DepositDtos.AdminDepositFilterRequest;
import com.banksystem.account.api.dto.DepositDtos.AdminTermDepositRow;
import com.banksystem.account.api.dto.DepositDtos.BatchRunResponse;
import com.banksystem.account.api.dto.DepositDtos.DepositAdminSummaryResponse;
import com.banksystem.account.api.dto.DepositDtos.DepositProductResponse;
import com.banksystem.account.api.dto.DepositDtos.UpdateDepositProductRequest;
import com.banksystem.account.application.DepositAdminService;
import com.banksystem.account.application.DepositBatchService;
import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.api.PageResponse;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.common.security.UserContext;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff term-deposit operations: funding summary + manual batch run. HTTP + permission only;
 * rules in application services. Gateway: {@code /api/v1/admin/deposits/**} → ACCOUNT-SERVICE.
 */
@RestController
@RequestMapping("/api/v1/admin/deposits")
public class AdminDepositController {

  private final DepositAdminService adminService;
  private final DepositBatchService batchService;

  public AdminDepositController(
      DepositAdminService adminService, DepositBatchService batchService) {
    this.adminService = adminService;
    this.batchService = batchService;
  }

  @GetMapping("/summary")
  @RequirePermission("deposits:summary:view")
  public ApiResponse<DepositAdminSummaryResponse> summary() {
    return ApiResponse.ok(adminService.summary());
  }

  /** Per-contract drill-down with optional filters (status, product, user, account, maturity). */
  @GetMapping
  @RequirePermission("deposits:summary:view")
  public ApiResponse<PageResponse<AdminTermDepositRow>> list(@Valid @ModelAttribute AdminDepositFilterRequest req) {
    return ApiResponse.ok(adminService.list(req));
  }

  @GetMapping("/products")
  @RequirePermission("deposits:summary:view")
  public ApiResponse<List<DepositProductResponse>> products() {
    return ApiResponse.ok(adminService.allProducts());
  }

  /** Adjust product rates/minimum/active. Existing contracts keep their rate snapshots. */
  @PatchMapping("/products/{code}")
  @RequirePermission("deposits:products:manage")
  public ApiResponse<DepositProductResponse> updateProduct(
      @PathVariable String code, @Valid @RequestBody UpdateDepositProductRequest request) {
    return ApiResponse.ok(
        adminService.updateProduct(code, request, UserContext.requireUser().userId()));
  }

  /** Synchronous manual accrual + maturity run (same job the nightly scheduler executes). */
  @PostMapping("/batch")
  @RequirePermission("deposits:batch:execute")
  public ApiResponse<BatchRunResponse> runBatch() {
    return ApiResponse.ok(batchService.run());
  }
}
