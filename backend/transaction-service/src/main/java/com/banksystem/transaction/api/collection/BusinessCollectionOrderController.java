package com.banksystem.transaction.api.collection;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.GatewayUser;
import com.banksystem.common.security.UserContext;
import com.banksystem.transaction.api.dto.CollectionDtos.CollectionOrderResponse;
import com.banksystem.transaction.api.dto.CollectionDtos.CreateCollectionOrderRequest;
import com.banksystem.transaction.api.dto.MerchantDtos.BusinessDashboardSummaryResponse;
import com.banksystem.transaction.api.dto.SettlementDtos.SettlementResponse;
import com.banksystem.transaction.application.collection.CollectionOrderService;
import com.banksystem.transaction.application.settlement.SettlementOrchestrator;
import com.banksystem.transaction.domain.collection.CollectionOrderStatus;
import com.banksystem.transaction.infrastructure.security.RequireBusinessPermission;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/businesses/{businessId}")
public class BusinessCollectionOrderController {

  private final CollectionOrderService collectionOrderService;
  private final SettlementOrchestrator settlementOrchestrator;

  public BusinessCollectionOrderController(
      CollectionOrderService collectionOrderService,
      SettlementOrchestrator settlementOrchestrator) {
    this.collectionOrderService = collectionOrderService;
    this.settlementOrchestrator = settlementOrchestrator;
  }

  @PostMapping("/collection-orders")
  @ResponseStatus(HttpStatus.CREATED)
  @RequireBusinessPermission(value = "business:orders:manage", businessIdParam = "businessId")
  public ApiResponse<CollectionOrderResponse> createOrder(
      @PathVariable UUID businessId,
      @Valid @RequestBody CreateCollectionOrderRequest request,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    return ApiResponse.ok(collectionOrderService.createCollectionOrder(businessId, request, idempotencyKey));
  }

  @GetMapping("/collection-orders")
  @RequireBusinessPermission(value = "business:orders:view", businessIdParam = "businessId")
  public ApiResponse<List<CollectionOrderResponse>> searchOrders(
      @PathVariable UUID businessId,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) CollectionOrderStatus status) {
    return ApiResponse.ok(collectionOrderService.searchList(businessId, q, status));
  }

  @GetMapping("/collection-orders/{id}")
  @RequireBusinessPermission(value = "business:orders:view", businessIdParam = "businessId")
  public ApiResponse<CollectionOrderResponse> getById(
      @PathVariable UUID businessId,
      @PathVariable UUID id) {
    return ApiResponse.ok(collectionOrderService.getById(businessId, id));
  }

  @GetMapping("/collection-orders/by-merchant-id/{merchantOrderId}")
  @RequireBusinessPermission(value = "business:orders:view", businessIdParam = "businessId")
  public ApiResponse<CollectionOrderResponse> getByMerchantOrderId(
      @PathVariable UUID businessId,
      @PathVariable String merchantOrderId) {
    return ApiResponse.ok(collectionOrderService.getByMerchantOrderId(businessId, merchantOrderId));
  }

  @PostMapping("/collection-orders/{id}/cancel")
  @RequireBusinessPermission(value = "business:orders:manage", businessIdParam = "businessId")
  public ApiResponse<Void> cancelOrder(
      @PathVariable UUID businessId,
      @PathVariable UUID id) {
    collectionOrderService.cancelOrder(businessId, id);
    return ApiResponse.ok(null);
  }

  @PostMapping("/collection-orders/{id}/complete")
  @RequireBusinessPermission(value = "business:settlements:execute", businessIdParam = "businessId")
  public ApiResponse<SettlementResponse> completeOrder(
      @PathVariable UUID businessId,
      @PathVariable UUID id) {
    GatewayUser user = UserContext.requireUser();
    return ApiResponse.ok(settlementOrchestrator.completeOrder(businessId, id, user.userId(), "BUSINESS_MEMBER"));
  }

  @GetMapping("/dashboard/summary")
  @RequireBusinessPermission(value = "business:dashboard:view", businessIdParam = "businessId")
  public ApiResponse<BusinessDashboardSummaryResponse> getDashboardSummary(
      @PathVariable UUID businessId) {
    return ApiResponse.ok(collectionOrderService.getDashboardSummary(businessId));
  }
}
