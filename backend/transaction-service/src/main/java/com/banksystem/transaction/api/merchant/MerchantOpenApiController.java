package com.banksystem.transaction.api.merchant;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.api.dto.CollectionDtos.CollectionOrderResponse;
import com.banksystem.transaction.api.dto.CollectionDtos.CreateCollectionOrderRequest;
import com.banksystem.transaction.api.dto.VirtualAccountDtos.ProvisionVirtualAccountRequest;
import com.banksystem.transaction.api.dto.VirtualAccountDtos.VirtualAccountResponse;
import com.banksystem.transaction.application.collection.CollectionOrderService;
import com.banksystem.transaction.application.virtualaccount.VirtualAccountService;
import com.banksystem.transaction.infrastructure.security.MerchantApiAuthInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/merchant")
public class MerchantOpenApiController {

  private final CollectionOrderService collectionOrderService;
  private final VirtualAccountService virtualAccountService;

  public MerchantOpenApiController(
      CollectionOrderService collectionOrderService,
      VirtualAccountService virtualAccountService) {
    this.collectionOrderService = collectionOrderService;
    this.virtualAccountService = virtualAccountService;
  }

  @PostMapping("/orders")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<CollectionOrderResponse> createOrder(
      HttpServletRequest servletRequest,
      @Valid @RequestBody CreateCollectionOrderRequest request,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    UUID orgId = getMerchantOrgId(servletRequest);
    return ApiResponse.ok(collectionOrderService.createCollectionOrder(orgId, request, idempotencyKey));
  }

  @GetMapping("/orders/{merchantOrderId}")
  public ApiResponse<CollectionOrderResponse> getOrder(
      HttpServletRequest servletRequest,
      @PathVariable String merchantOrderId) {
    UUID orgId = getMerchantOrgId(servletRequest);
    return ApiResponse.ok(collectionOrderService.getByMerchantOrderId(orgId, merchantOrderId));
  }

  @PostMapping("/virtual-accounts")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<VirtualAccountResponse> provisionVirtualAccount(
      HttpServletRequest servletRequest,
      @Valid @RequestBody ProvisionVirtualAccountRequest request) {
    UUID orgId = getMerchantOrgId(servletRequest);
    return ApiResponse.ok(virtualAccountService.provision(orgId, request));
  }

  private UUID getMerchantOrgId(HttpServletRequest request) {
    Object orgIdObj = request.getAttribute(MerchantApiAuthInterceptor.ATTR_ORGANIZATION_ID);
    if (orgIdObj instanceof UUID u) {
      return u;
    }
    throw new BusinessException("UNAUTHORIZED", "Merchant organization context missing");
  }
}
