package com.banksystem.transaction.api.settlement;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.GatewayUser;
import com.banksystem.common.security.UserContext;
import com.banksystem.transaction.api.dto.SettlementDtos.SettlementPreviewRequest;
import com.banksystem.transaction.api.dto.SettlementDtos.SettlementPreviewResponse;
import com.banksystem.transaction.api.dto.SettlementDtos.SettlementResponse;
import com.banksystem.transaction.application.settlement.SettlementOrchestrator;
import com.banksystem.transaction.application.settlement.SplitRuleService;
import com.banksystem.transaction.domain.settlement.SettlementStatus;
import com.banksystem.transaction.infrastructure.security.RequireBusinessPermission;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/businesses/{businessId}/settlements")
public class BusinessSettlementController {

  private final SettlementOrchestrator settlementOrchestrator;
  private final SplitRuleService splitRuleService;

  public BusinessSettlementController(
      SettlementOrchestrator settlementOrchestrator,
      SplitRuleService splitRuleService) {
    this.settlementOrchestrator = settlementOrchestrator;
    this.splitRuleService = splitRuleService;
  }

  @GetMapping
  @RequireBusinessPermission(value = "business:settlements:view", businessIdParam = "businessId")
  public ApiResponse<List<SettlementResponse>> search(
      @PathVariable UUID businessId,
      @RequestParam(required = false) SettlementStatus status) {
    return ApiResponse.ok(settlementOrchestrator.searchList(businessId, status));
  }

  @GetMapping("/{id}")
  @RequireBusinessPermission(value = "business:settlements:view", businessIdParam = "businessId")
  public ApiResponse<SettlementResponse> getById(
      @PathVariable UUID businessId,
      @PathVariable UUID id) {
    return ApiResponse.ok(settlementOrchestrator.getById(businessId, id));
  }

  @PostMapping("/{id}/retry")
  @RequireBusinessPermission(value = "business:settlements:execute", businessIdParam = "businessId")
  public ApiResponse<SettlementResponse> retry(
      @PathVariable UUID businessId,
      @PathVariable UUID id) {
    GatewayUser user = UserContext.requireUser();
    return ApiResponse.ok(settlementOrchestrator.retrySettlement(businessId, id, user.userId(), "BUSINESS_MEMBER"));
  }

  @PostMapping("/preview")
  @RequireBusinessPermission(value = "business:settlements:view", businessIdParam = "businessId")
  public ApiResponse<SettlementPreviewResponse> preview(
      @PathVariable UUID businessId,
      @Valid @RequestBody SettlementPreviewRequest request) {
    return ApiResponse.ok(splitRuleService.preview(request));
  }
}
