package com.banksystem.transaction.api.forensics;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.GatewayUser;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.common.security.SecurityHeaders;
import com.banksystem.common.security.UserContext;
import com.banksystem.transaction.api.dto.RemediationProposalDtos.CreateRemediationProposalRequest;
import com.banksystem.transaction.api.dto.RemediationProposalDtos.RejectRemediationProposalRequest;
import com.banksystem.transaction.api.dto.RemediationProposalDtos.RemediationProposalResponse;
import com.banksystem.transaction.api.dto.RemediationProposalDtos.UpdateRemediationProposalRequest;
import com.banksystem.transaction.application.forensics.RemediationProposalCommandService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/forensics/proposals")
public class AdminRemediationProposalController {

  private final RemediationProposalCommandService commandService;

  public AdminRemediationProposalController(RemediationProposalCommandService commandService) {
    this.commandService = commandService;
  }

  @PostMapping
  @RequirePermission(SecurityHeaders.PERM_FORENSICS_CASE_REVIEW)
  public ApiResponse<RemediationProposalResponse> createDraft(
      @Valid @RequestBody CreateRemediationProposalRequest request) {
    GatewayUser actor = UserContext.requireUser();
    return ApiResponse.ok(commandService.createDraft(request, actor.userId()));
  }

  @PutMapping("/{id}")
  @RequirePermission(SecurityHeaders.PERM_FORENSICS_CASE_REVIEW)
  public ApiResponse<RemediationProposalResponse> updateDraft(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateRemediationProposalRequest request) {
    GatewayUser actor = UserContext.requireUser();
    return ApiResponse.ok(commandService.updateDraft(id, request, actor.userId()));
  }

  @PostMapping("/{id}/submit")
  @RequirePermission(SecurityHeaders.PERM_FORENSICS_CASE_REVIEW)
  public ApiResponse<RemediationProposalResponse> submit(
      @PathVariable UUID id,
      @RequestParam long expectedVersion) {
    GatewayUser actor = UserContext.requireUser();
    return ApiResponse.ok(commandService.submit(id, actor.userId(), expectedVersion));
  }

  @PostMapping("/{id}/approve")
  @RequirePermission(SecurityHeaders.PERM_FORENSICS_CASE_REVIEW)
  public ApiResponse<RemediationProposalResponse> approve(
      @PathVariable UUID id,
      @RequestParam long expectedVersion) {
    GatewayUser actor = UserContext.requireUser();
    return ApiResponse.ok(commandService.approveProposal(id, actor.userId(), expectedVersion));
  }

  @PostMapping("/{id}/reject")
  @RequirePermission(SecurityHeaders.PERM_FORENSICS_CASE_REVIEW)
  public ApiResponse<RemediationProposalResponse> reject(
      @PathVariable UUID id,
      @Valid @RequestBody RejectRemediationProposalRequest request) {
    GatewayUser actor = UserContext.requireUser();
    return ApiResponse.ok(commandService.reject(id, request, actor.userId()));
  }

  @PostMapping("/{id}/cancel")
  @RequirePermission(SecurityHeaders.PERM_FORENSICS_CASE_REVIEW)
  public ApiResponse<RemediationProposalResponse> cancel(
      @PathVariable UUID id,
      @RequestParam long expectedVersion) {
    GatewayUser actor = UserContext.requireUser();
    return ApiResponse.ok(commandService.cancel(id, actor.userId(), expectedVersion));
  }
}
