package com.banksystem.transaction.api;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.api.PageResponse;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.transaction.api.dto.ReconDtos.ReconRunDetailResponse;
import com.banksystem.transaction.api.dto.ReconDtos.ReconRunResponse;
import com.banksystem.transaction.api.dto.ReconDtos.RunReconRequest;
import com.banksystem.transaction.application.ReconciliationService;
import com.banksystem.transaction.domain.ReconRunEntity;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff reconciliation: browse runs/discrepancies, trigger a manual run. HTTP + permission only;
 * rules in {@link ReconciliationService}. Gateway: {@code /api/v1/admin/**} → TRANSACTION-SERVICE.
 */
@RestController
@RequestMapping("/api/v1/admin/recon")
public class AdminReconciliationController {

  private final ReconciliationService service;

  public AdminReconciliationController(ReconciliationService service) {
    this.service = service;
  }

  @GetMapping("/runs")
  @RequirePermission("transactions:recon:view")
  public ApiResponse<PageResponse<ReconRunResponse>> list(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return ApiResponse.ok(service.list(page, size));
  }

  @GetMapping("/runs/{id}")
  @RequirePermission("transactions:recon:view")
  public ApiResponse<ReconRunDetailResponse> get(@PathVariable UUID id) {
    return ApiResponse.ok(service.get(id));
  }

  /** Synchronous manual run for one banking date (re-running a date creates a new run). */
  @PostMapping("/runs")
  @RequirePermission("transactions:recon:execute")
  public ApiResponse<ReconRunResponse> run(@Valid @RequestBody RunReconRequest request) {
    return ApiResponse.ok(service.runForDate(request.date(), ReconRunEntity.TRIGGER_MANUAL));
  }
}
