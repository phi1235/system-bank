package com.banksystem.transaction.api.collection;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.GatewayUser;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.common.security.SecurityHeaders;
import com.banksystem.common.security.UserContext;
import com.banksystem.transaction.api.dto.CollectionDtos.CollectionOrderResponse;
import com.banksystem.transaction.api.dto.SettlementDtos.SettlementResponse;
import com.banksystem.transaction.application.collection.CollectionOrderService;
import com.banksystem.transaction.application.settlement.SettlementOrchestrator;
import com.banksystem.transaction.domain.collection.CollectionOrderStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/collection-orders")
public class AdminCollectionOrderController {

  private final CollectionOrderService collectionOrderService;
  private final SettlementOrchestrator settlementOrchestrator;

  public AdminCollectionOrderController(
      CollectionOrderService collectionOrderService,
      SettlementOrchestrator settlementOrchestrator) {
    this.collectionOrderService = collectionOrderService;
    this.settlementOrchestrator = settlementOrchestrator;
  }

  @GetMapping
  @RequirePermission(SecurityHeaders.PERM_VA_OPERATIONS_VIEW)
  public ApiResponse<List<CollectionOrderResponse>> searchOrders(
      @RequestParam(required = false) UUID organizationId,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) CollectionOrderStatus status) {
    return ApiResponse.ok(collectionOrderService.searchList(organizationId, q, status));
  }

  @GetMapping("/{id}")
  @RequirePermission(SecurityHeaders.PERM_VA_OPERATIONS_VIEW)
  public ApiResponse<CollectionOrderResponse> getById(@PathVariable UUID id) {
    return ApiResponse.ok(collectionOrderService.getById(null, id));
  }

  @PostMapping("/{id}/complete")
  @RequirePermission(SecurityHeaders.PERM_SETTLEMENT_APPROVE)
  public ApiResponse<SettlementResponse> completeOrder(@PathVariable UUID id) {
    GatewayUser user = UserContext.requireUser();
    return ApiResponse.ok(settlementOrchestrator.completeOrder(null, id, user.userId(), "STAFF"));
  }
}
