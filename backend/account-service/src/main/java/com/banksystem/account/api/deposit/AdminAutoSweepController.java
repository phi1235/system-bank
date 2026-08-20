package com.banksystem.account.api.deposit;

import com.banksystem.account.api.dto.AutoSweepDtos.AutoSweepBatchResponse;
import com.banksystem.account.api.dto.AutoSweepDtos.AutoSweepListRequest;
import com.banksystem.account.api.dto.AutoSweepDtos.AutoSweepProfileResponse;
import com.banksystem.account.application.sweep.AutoSweepQueries.ListQuery;
import com.banksystem.account.application.sweep.AutoSweepBatchService;
import com.banksystem.account.application.sweep.AutoSweepService;
import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.api.PageResponse;
import com.banksystem.common.security.RequirePermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/deposits/auto-sweep")
public class AdminAutoSweepController {
  private final AutoSweepService service;
  private final AutoSweepBatchService batchService;

  public AdminAutoSweepController(AutoSweepService service, AutoSweepBatchService batchService) {
    this.service = service;
    this.batchService = batchService;
  }

  @GetMapping
  @RequirePermission("deposits:summary:view")
  public ApiResponse<PageResponse<AutoSweepProfileResponse>> profiles(
      @Valid @ModelAttribute AutoSweepListRequest request) {
    return ApiResponse.ok(service.listAll(ListQuery.of(request)));
  }

  @PostMapping("/batch")
  @RequirePermission("deposits:batch:execute")
  public ApiResponse<AutoSweepBatchResponse> runBatch() {
    return ApiResponse.ok(batchService.run());
  }
}
