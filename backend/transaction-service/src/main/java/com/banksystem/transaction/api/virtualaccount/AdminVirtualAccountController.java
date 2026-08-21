package com.banksystem.transaction.api.virtualaccount;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.common.security.SecurityHeaders;
import com.banksystem.transaction.api.dto.CollectionDtos.InboundPaymentEventResponse;
import com.banksystem.transaction.api.dto.VirtualAccountDtos.VirtualAccountResponse;
import com.banksystem.transaction.application.collection.InboundPaymentQueryService;
import com.banksystem.transaction.application.virtualaccount.VirtualAccountService;
import com.banksystem.transaction.domain.collection.InboundPaymentStatus;
import com.banksystem.transaction.domain.virtualaccount.VirtualAccountStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/virtual-accounts")
public class AdminVirtualAccountController {

  private final VirtualAccountService virtualAccountService;
  private final InboundPaymentQueryService inboundPaymentQueryService;

  public AdminVirtualAccountController(
      VirtualAccountService virtualAccountService,
      InboundPaymentQueryService inboundPaymentQueryService) {
    this.virtualAccountService = virtualAccountService;
    this.inboundPaymentQueryService = inboundPaymentQueryService;
  }

  @GetMapping
  @RequirePermission(SecurityHeaders.PERM_VA_OPERATIONS_VIEW)
  public ApiResponse<List<VirtualAccountResponse>> searchVirtualAccounts(
      @RequestParam(required = false) UUID organizationId,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) VirtualAccountStatus status) {
    return ApiResponse.ok(virtualAccountService.searchList(organizationId, q, status));
  }

  @GetMapping("/inbound-events")
  @RequirePermission(SecurityHeaders.PERM_VA_OPERATIONS_VIEW)
  public ApiResponse<List<InboundPaymentEventResponse>> searchInboundEvents(
      @RequestParam(required = false) String provider,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) InboundPaymentStatus status) {
    return ApiResponse.ok(inboundPaymentQueryService.searchList(provider, q, status));
  }
}
