package com.banksystem.transaction.api.risk;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.common.security.UserContext;
import com.banksystem.transaction.api.dto.RiskDtos.BlacklistRequest;
import com.banksystem.transaction.api.dto.RiskDtos.BlacklistResponse;
import com.banksystem.transaction.api.dto.RiskDtos.RiskDecisionRequest;
import com.banksystem.transaction.api.dto.RiskDtos.RiskRuleRequest;
import com.banksystem.transaction.api.dto.RiskDtos.RiskRuleResponse;
import com.banksystem.transaction.api.dto.TransferDtos.TransferResponse;
import com.banksystem.transaction.application.mapper.TransferMapper;
import com.banksystem.transaction.application.risk.RiskAdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/risk")
public class AdminRiskController {

  private final RiskAdminService service;
  private final TransferMapper transferMapper;

  public AdminRiskController(RiskAdminService service, TransferMapper transferMapper) {
    this.service = service;
    this.transferMapper = transferMapper;
  }

  @GetMapping("/rules")
  @RequirePermission("risk:view")
  public ApiResponse<List<RiskRuleResponse>> rules() {
    return ApiResponse.ok(service.rules());
  }

  @PostMapping("/rules")
  @RequirePermission("risk:manage")
  public ApiResponse<RiskRuleResponse> createRule(@Valid @RequestBody RiskRuleRequest request) {
    return ApiResponse.ok(service.saveRule(null, request));
  }

  @PutMapping("/rules/{id}")
  @RequirePermission("risk:manage")
  public ApiResponse<RiskRuleResponse> updateRule(
      @PathVariable UUID id, @Valid @RequestBody RiskRuleRequest request) {
    return ApiResponse.ok(service.saveRule(id, request));
  }

  @GetMapping("/blacklist")
  @RequirePermission("risk:view")
  public ApiResponse<List<BlacklistResponse>> blacklist() {
    return ApiResponse.ok(service.blacklist());
  }

  @PostMapping("/blacklist")
  @RequirePermission("risk:manage")
  public ApiResponse<BlacklistResponse> addBlacklist(
      @Valid @RequestBody BlacklistRequest request) {
    return ApiResponse.ok(service.addBlacklist(request, UserContext.requireUser().userId()));
  }

  @PostMapping("/blacklist/{id}/deactivate")
  @RequirePermission("risk:manage")
  public ApiResponse<BlacklistResponse> deactivateBlacklist(@PathVariable UUID id) {
    return ApiResponse.ok(service.deactivateBlacklist(id));
  }

  @PostMapping("/transfers/{id}/approve")
  @RequirePermission("risk:decide")
  public ApiResponse<TransferResponse> approve(
      @PathVariable UUID id,
      @Valid @RequestBody RiskDecisionRequest request,
      HttpServletRequest http) {
    return ApiResponse.ok(transferMapper.toResponse(service.approveTransfer(
        id, UserContext.requireUser().userId(), request.note(), UserContext.clientIp(http))));
  }

  @PostMapping("/transfers/{id}/reject")
  @RequirePermission("risk:decide")
  public ApiResponse<TransferResponse> reject(
      @PathVariable UUID id,
      @Valid @RequestBody RiskDecisionRequest request,
      HttpServletRequest http) {
    return ApiResponse.ok(transferMapper.toResponse(service.rejectTransfer(
        id, UserContext.requireUser().userId(), request.note(), UserContext.clientIp(http))));
  }
}
