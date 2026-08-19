package com.banksystem.transaction.api.transfer;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.api.PageResponse;
import com.banksystem.common.security.GatewayUser;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.common.security.SecurityHeaders;
import com.banksystem.common.security.UserContext;
import com.banksystem.transaction.api.dto.TransferDtos.TransferResponse;
import com.banksystem.transaction.application.mapper.TransferMapper;
import com.banksystem.transaction.application.transfer.impl.TransferSagaOrchestrator;
import com.banksystem.transaction.domain.transfer.TransferOrderEntity;
import com.banksystem.transaction.domain.transfer.TransferOrderRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

  private final TransferOrderRepository transferOrderRepository;
  private final TransferSagaOrchestrator sagaOrchestrator;
  private final TransferMapper mapper;

  public AdminTransferReconciliationController(
      TransferOrderRepository transferOrderRepository,
      TransferSagaOrchestrator sagaOrchestrator,
      TransferMapper mapper) {
    this.transferOrderRepository = transferOrderRepository;
    this.sagaOrchestrator = sagaOrchestrator;
    this.mapper = mapper;
  }

  @GetMapping("/manual-review")
  @RequirePermission(SecurityHeaders.PERM_TX_RECON_VIEW)
  public ApiResponse<PageResponse<TransferResponse>> listManualReviewOrders(
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size) {
    int safePage = Math.max(0, page);
    int safeSize = Math.min(100, Math.max(1, size));
    Page<TransferOrderEntity> pageResult = transferOrderRepository
        .findManualReviewOrders(PageRequest.of(safePage, safeSize));
    List<TransferResponse> content = pageResult.getContent().stream()
        .map(mapper::toResponse)
        .toList();
    return ApiResponse.ok(new PageResponse<>(
        content,
        pageResult.getNumber(),
        pageResult.getSize(),
        pageResult.getTotalElements(),
        pageResult.getTotalPages()));
  }

  @PostMapping("/{id}/force-settle")
  @RequirePermission(SecurityHeaders.PERM_TX_RECON_MANAGE)
  public ApiResponse<TransferResponse> forceSettle(
      @PathVariable("id") UUID id,
      @Valid @RequestBody ForceActionRequest request) {
    GatewayUser admin = UserContext.requireUser();
    TransferOrderEntity settled = sagaOrchestrator.forceSettle(id, admin.userId(), request.reason());
    return ApiResponse.ok(mapper.toResponse(settled));
  }

  @PostMapping("/{id}/force-refund")
  @RequirePermission(SecurityHeaders.PERM_TX_RECON_MANAGE)
  public ApiResponse<TransferResponse> forceRefund(
      @PathVariable("id") UUID id,
      @Valid @RequestBody ForceActionRequest request) {
    GatewayUser admin = UserContext.requireUser();
    TransferOrderEntity refunded = sagaOrchestrator.forceRefund(id, admin.userId(), request.reason());
    return ApiResponse.ok(mapper.toResponse(refunded));
  }
}
