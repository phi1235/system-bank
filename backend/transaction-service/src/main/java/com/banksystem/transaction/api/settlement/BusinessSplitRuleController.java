package com.banksystem.transaction.api.settlement;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.transaction.api.dto.SettlementDtos.CreateSplitRuleRequest;
import com.banksystem.transaction.api.dto.SettlementDtos.SplitRuleResponse;
import com.banksystem.transaction.application.settlement.SplitRuleService;
import com.banksystem.transaction.infrastructure.security.RequireBusinessPermission;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/businesses/{businessId}/split-rules")
public class BusinessSplitRuleController {

  private final SplitRuleService splitRuleService;

  public BusinessSplitRuleController(SplitRuleService splitRuleService) {
    this.splitRuleService = splitRuleService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @RequireBusinessPermission(value = "business:split:manage", businessIdParam = "businessId")
  public ApiResponse<SplitRuleResponse> create(
      @PathVariable UUID businessId,
      @Valid @RequestBody CreateSplitRuleRequest request) {
    return ApiResponse.ok(splitRuleService.createSplitRule(businessId, request));
  }

  @GetMapping
  @RequireBusinessPermission(value = "business:split:view", businessIdParam = "businessId")
  public ApiResponse<List<SplitRuleResponse>> list(@PathVariable UUID businessId) {
    return ApiResponse.ok(splitRuleService.listSplitRules(businessId));
  }

  @GetMapping("/{id}")
  @RequireBusinessPermission(value = "business:split:view", businessIdParam = "businessId")
  public ApiResponse<SplitRuleResponse> getById(
      @PathVariable UUID businessId,
      @PathVariable UUID id) {
    return ApiResponse.ok(splitRuleService.getSplitRule(businessId, id));
  }

  @DeleteMapping("/{id}")
  @RequireBusinessPermission(value = "business:split:manage", businessIdParam = "businessId")
  public ApiResponse<Void> delete(
      @PathVariable UUID businessId,
      @PathVariable UUID id) {
    splitRuleService.deleteSplitRule(businessId, id);
    return ApiResponse.ok(null);
  }
}
