package com.banksystem.account.api.deposit;

import com.banksystem.account.api.dto.AutoSweepDtos.AutoSweepOperationResponse;
import com.banksystem.account.api.dto.AutoSweepDtos.AutoSweepOperationsRequest;
import com.banksystem.account.api.dto.AutoSweepDtos.AutoSweepProfileResponse;
import com.banksystem.account.api.dto.AutoSweepDtos.SweepProductResponse;
import com.banksystem.account.api.dto.AutoSweepDtos.UpsertAutoSweepRequest;
import com.banksystem.account.application.sweep.AutoSweepService;
import com.banksystem.account.application.sweep.AutoSweepQueries.OperationsQuery;
import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.common.security.UserContext;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/deposits/auto-sweep")
public class CustomerAutoSweepController {
  private final AutoSweepService service;

  public CustomerAutoSweepController(AutoSweepService service) {
    this.service = service;
  }

  @GetMapping("/products")
  @RequirePermission("ib:wealth:view")
  public ApiResponse<List<SweepProductResponse>> products() {
    return ApiResponse.ok(service.products());
  }

  @GetMapping
  @RequirePermission("ib:wealth:view")
  public ApiResponse<List<AutoSweepProfileResponse>> mine() {
    return ApiResponse.ok(service.listMine(UserContext.requireUser().userId()));
  }

  @PutMapping("/{accountId}")
  @RequirePermission("ib:wealth:auto-sweep:manage")
  public ApiResponse<AutoSweepProfileResponse> upsert(
      @PathVariable UUID accountId, @Valid @RequestBody UpsertAutoSweepRequest request) {
    return ApiResponse.ok(service.upsert(accountId, request, UserContext.requireUser()));
  }

  @PostMapping("/{accountId}/pause")
  @RequirePermission("ib:wealth:auto-sweep:manage")
  public ApiResponse<AutoSweepProfileResponse> pause(@PathVariable UUID accountId) {
    return ApiResponse.ok(service.setEnabled(accountId, false, UserContext.requireUser()));
  }

  @PostMapping("/{accountId}/resume")
  @RequirePermission("ib:wealth:auto-sweep:manage")
  public ApiResponse<AutoSweepProfileResponse> resume(@PathVariable UUID accountId) {
    return ApiResponse.ok(service.setEnabled(accountId, true, UserContext.requireUser()));
  }

  @GetMapping("/{accountId}/operations")
  @RequirePermission("ib:wealth:view")
  public ApiResponse<List<AutoSweepOperationResponse>> operations(
      @PathVariable UUID accountId, @Valid @ModelAttribute AutoSweepOperationsRequest request) {
    return ApiResponse.ok(service.operations(
        accountId, UserContext.requireUser().userId(), OperationsQuery.of(request)));
  }
}
