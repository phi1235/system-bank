package com.banksystem.corporate.api;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.GatewayUser;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.common.security.UserContext;
import com.banksystem.corporate.api.dto.ApprovalPolicyDtos.ApprovalPolicyResponse;
import com.banksystem.corporate.api.dto.ApprovalPolicyDtos.CreateApprovalPolicyRequest;
import com.banksystem.corporate.api.dto.ApprovalPolicyDtos.SimulateApprovalPlanRequest;
import com.banksystem.corporate.api.dto.ApprovalPolicyDtos.SimulateApprovalPlanResponse;
import com.banksystem.corporate.application.approval.ApprovalMatrixService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/corporations/{corporateId}/approval-policies")
public class ApprovalPolicyController {

  private final ApprovalMatrixService approvalMatrixService;

  public ApprovalPolicyController(ApprovalMatrixService approvalMatrixService) {
    this.approvalMatrixService = approvalMatrixService;
  }

  @PostMapping
  @RequirePermission("corp:portal:view")
  public ResponseEntity<ApiResponse<ApprovalPolicyResponse>> createPolicy(
      @PathVariable("corporateId") UUID corporateId,
      @Valid @RequestBody CreateApprovalPolicyRequest req) {
    GatewayUser user = UserContext.requireUser();
    ApprovalPolicyResponse res = approvalMatrixService.createPolicy(corporateId, user.userId(), req);
    return ResponseEntity.ok(ApiResponse.ok(res));
  }

  @GetMapping
  @RequirePermission("corp:portal:view")
  public ResponseEntity<ApiResponse<List<ApprovalPolicyResponse>>> listPolicies(
      @PathVariable("corporateId") UUID corporateId) {
    GatewayUser user = UserContext.requireUser();
    List<ApprovalPolicyResponse> list = approvalMatrixService.listPolicies(corporateId, user.userId());
    return ResponseEntity.ok(ApiResponse.ok(list));
  }

  @GetMapping("/active")
  @RequirePermission("corp:portal:view")
  public ResponseEntity<ApiResponse<ApprovalPolicyResponse>> getActivePolicy(
      @PathVariable("corporateId") UUID corporateId) {
    GatewayUser user = UserContext.requireUser();
    ApprovalPolicyResponse res = approvalMatrixService.getActivePolicy(corporateId, user.userId());
    return ResponseEntity.ok(ApiResponse.ok(res));
  }

  @GetMapping("/{policyId}")
  @RequirePermission("corp:portal:view")
  public ResponseEntity<ApiResponse<ApprovalPolicyResponse>> getPolicy(
      @PathVariable("corporateId") UUID corporateId,
      @PathVariable("policyId") UUID policyId) {
    GatewayUser user = UserContext.requireUser();
    ApprovalPolicyResponse res = approvalMatrixService.getPolicy(corporateId, user.userId(), policyId);
    return ResponseEntity.ok(ApiResponse.ok(res));
  }

  @PutMapping("/{policyId}/activate")
  @RequirePermission("corp:portal:view")
  public ResponseEntity<ApiResponse<ApprovalPolicyResponse>> activatePolicy(
      @PathVariable("corporateId") UUID corporateId,
      @PathVariable("policyId") UUID policyId) {
    GatewayUser user = UserContext.requireUser();
    ApprovalPolicyResponse res = approvalMatrixService.activatePolicy(corporateId, user.userId(), policyId);
    return ResponseEntity.ok(ApiResponse.ok(res));
  }

  @PutMapping("/{policyId}/retire")
  @RequirePermission("corp:portal:view")
  public ResponseEntity<ApiResponse<ApprovalPolicyResponse>> retirePolicy(
      @PathVariable("corporateId") UUID corporateId,
      @PathVariable("policyId") UUID policyId) {
    GatewayUser user = UserContext.requireUser();
    ApprovalPolicyResponse res = approvalMatrixService.retirePolicy(corporateId, user.userId(), policyId);
    return ResponseEntity.ok(ApiResponse.ok(res));
  }

  @PostMapping("/simulate")
  @RequirePermission("corp:portal:view")
  public ResponseEntity<ApiResponse<SimulateApprovalPlanResponse>> simulatePlan(
      @PathVariable("corporateId") UUID corporateId,
      @Valid @RequestBody SimulateApprovalPlanRequest req) {
    GatewayUser user = UserContext.requireUser();
    SimulateApprovalPlanResponse res = approvalMatrixService.simulateApprovalPlan(
        corporateId, user.userId(), req.totalAmount(), req.currency());
    return ResponseEntity.ok(ApiResponse.ok(res));
  }
}
