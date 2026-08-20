package com.banksystem.transaction.api.transfer;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.api.PageResponse;
import com.banksystem.common.security.GatewayUser;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.common.security.SecurityHeaders;
import com.banksystem.common.security.UserContext;
import com.banksystem.transaction.api.dto.TransferDtos.TransferResponse;
import com.banksystem.transaction.application.transfer.TransferQueryService;
import com.banksystem.transaction.application.transfer.impl.TransferSagaOrchestrator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/transfers")
public class AdminTransferReconciliationController {

  public record ForceActionRequest(@NotBlank String reason) {}

  private final TransferQueryService transferQueryService;
  private final TransferSagaOrchestrator sagaOrchestrator;

  public AdminTransferReconciliationController(
      TransferQueryService transferQueryService,
      TransferSagaOrchestrator sagaOrchestrator) {
    this.transferQueryService = transferQueryService;
    this.sagaOrchestrator = sagaOrchestrator;
  }

  @GetMapping("/manual-review")
  @RequirePermission(SecurityHeaders.PERM_TX_RECON_VIEW)
  public ApiResponse<PageResponse<TransferResponse>> listManualReviewOrders(
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size) {
    return ApiResponse.ok(transferQueryService.manualReviewOrders(page, size));
  }

  @PostMapping("/{id}/force-settle")
  @RequirePermission(SecurityHeaders.PERM_TX_RECON_MANAGE)
  public ApiResponse<TransferResponse> forceSettle(
      @PathVariable("id") UUID id,
      @Valid @RequestBody ForceActionRequest request) {
    GatewayUser admin = UserContext.requireUser();
    sagaOrchestrator.forceSettle(id, admin.userId(), request.reason());
    return ApiResponse.ok(transferQueryService.get(id, admin));
  }

  @PostMapping("/{id}/force-refund")
  @RequirePermission(SecurityHeaders.PERM_TX_RECON_MANAGE)
  public ApiResponse<TransferResponse> forceRefund(
      @PathVariable("id") UUID id,
      @Valid @RequestBody ForceActionRequest request) {
    GatewayUser admin = UserContext.requireUser();
    sagaOrchestrator.forceRefund(id, admin.userId(), request.reason());
    return ApiResponse.ok(transferQueryService.get(id, admin));
  }
}
