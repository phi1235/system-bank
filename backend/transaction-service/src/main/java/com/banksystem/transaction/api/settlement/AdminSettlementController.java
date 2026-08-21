package com.banksystem.transaction.api.settlement;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.GatewayUser;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.common.security.SecurityHeaders;
import com.banksystem.common.security.UserContext;
import com.banksystem.transaction.api.dto.SettlementDtos.SettlementResponse;
import com.banksystem.transaction.application.settlement.SettlementOrchestrator;
import com.banksystem.transaction.domain.settlement.SettlementStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/settlements")
public class AdminSettlementController {

  private final SettlementOrchestrator settlementOrchestrator;

  public AdminSettlementController(SettlementOrchestrator settlementOrchestrator) {
    this.settlementOrchestrator = settlementOrchestrator;
  }

  @GetMapping
  @RequirePermission(SecurityHeaders.PERM_SETTLEMENT_VIEW)
  public ApiResponse<List<SettlementResponse>> search(
      @RequestParam(required = false) UUID organizationId,
      @RequestParam(required = false) SettlementStatus status) {
    return ApiResponse.ok(settlementOrchestrator.searchList(organizationId, status));
  }

  @GetMapping("/{id}")
  @RequirePermission(SecurityHeaders.PERM_SETTLEMENT_VIEW)
  public ApiResponse<SettlementResponse> getById(@PathVariable UUID id) {
    return ApiResponse.ok(settlementOrchestrator.getById(null, id));
  }

  @PostMapping("/{id}/retry")
  @RequirePermission(SecurityHeaders.PERM_SETTLEMENT_RETRY)
  public ApiResponse<SettlementResponse> retry(@PathVariable UUID id) {
    GatewayUser user = UserContext.requireUser();
    return ApiResponse.ok(settlementOrchestrator.retrySettlement(null, id, user.userId(), "STAFF"));
  }
}
