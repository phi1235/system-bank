package com.banksystem.corporate.api;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.GatewayUser;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.common.security.UserContext;
import com.banksystem.corporate.api.dto.ApprovalTaskDtos.ApprovalActionRequest;
import com.banksystem.corporate.api.dto.ApprovalTaskDtos.ApprovalInstanceDetailResponse;
import com.banksystem.corporate.api.dto.ApprovalTaskDtos.ApprovalTaskResponse;
import com.banksystem.corporate.api.dto.ApprovalTaskDtos.CreateChallengeResponse;
import com.banksystem.corporate.api.dto.ApprovalTaskDtos.RejectActionRequest;
import com.banksystem.corporate.api.dto.ApprovalTaskDtos.ReturnActionRequest;
import com.banksystem.corporate.application.approval.ApprovalWorkflowService;
import com.banksystem.corporate.application.approval.SigningChallengeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/approval-tasks")
public class ApprovalTaskController {

  private final ApprovalWorkflowService workflowService;
  private final SigningChallengeService signingChallengeService;

  public ApprovalTaskController(
      ApprovalWorkflowService workflowService,
      SigningChallengeService signingChallengeService) {
    this.workflowService = workflowService;
    this.signingChallengeService = signingChallengeService;
  }

  @GetMapping("/inbox")
  @RequirePermission("corp:portal:view")
  public ResponseEntity<ApiResponse<List<ApprovalTaskResponse>>> getInbox() {
    GatewayUser user = UserContext.requireUser();
    List<ApprovalTaskResponse> list = workflowService.getInbox(user.userId());
    return ResponseEntity.ok(ApiResponse.ok(list));
  }

  @GetMapping("/batches/{batchId}/instance")
  @RequirePermission("corp:portal:view")
  public ResponseEntity<ApiResponse<ApprovalInstanceDetailResponse>> getInstanceDetail(
      @PathVariable("batchId") UUID batchId) {
    GatewayUser user = UserContext.requireUser();
    ApprovalInstanceDetailResponse res = workflowService.getInstanceDetail(batchId, user.userId());
    return ResponseEntity.ok(ApiResponse.ok(res));
  }

  @PostMapping("/{taskId}/challenge")
  @RequirePermission("corp:portal:view")
  public ResponseEntity<ApiResponse<CreateChallengeResponse>> createChallenge(
      @PathVariable("taskId") UUID taskId) {
    GatewayUser user = UserContext.requireUser();
    CreateChallengeResponse res = signingChallengeService.createChallenge(taskId, user.userId());
    return ResponseEntity.ok(ApiResponse.ok(res));
  }

  @PostMapping("/{taskId}/approve")
  @RequirePermission("corp:portal:view")
  public ResponseEntity<ApiResponse<ApprovalTaskResponse>> approveTask(
      @PathVariable("taskId") UUID taskId,
      @Valid @RequestBody ApprovalActionRequest req,
      HttpServletRequest request) {
    GatewayUser user = UserContext.requireUser();
    String ip = request.getRemoteAddr();
    String userAgent = request.getHeader("User-Agent");
    ApprovalTaskResponse res = workflowService.approveTask(taskId, user.userId(), req, ip, userAgent);
    return ResponseEntity.ok(ApiResponse.ok(res));
  }

  @PostMapping("/{taskId}/reject")
  @RequirePermission("corp:portal:view")
  public ResponseEntity<ApiResponse<ApprovalTaskResponse>> rejectTask(
      @PathVariable("taskId") UUID taskId,
      @Valid @RequestBody RejectActionRequest req,
      HttpServletRequest request) {
    GatewayUser user = UserContext.requireUser();
    String ip = request.getRemoteAddr();
    String userAgent = request.getHeader("User-Agent");
    ApprovalTaskResponse res = workflowService.rejectTask(taskId, user.userId(), req.reason(), ip, userAgent);
    return ResponseEntity.ok(ApiResponse.ok(res));
  }

  @PostMapping("/{taskId}/return")
  @RequirePermission("corp:portal:view")
  public ResponseEntity<ApiResponse<ApprovalTaskResponse>> returnTask(
      @PathVariable("taskId") UUID taskId,
      @Valid @RequestBody ReturnActionRequest req,
      HttpServletRequest request) {
    GatewayUser user = UserContext.requireUser();
    String ip = request.getRemoteAddr();
    String userAgent = request.getHeader("User-Agent");
    ApprovalTaskResponse res = workflowService.returnTask(taskId, user.userId(), req.reason(), ip, userAgent);
    return ResponseEntity.ok(ApiResponse.ok(res));
  }
}
