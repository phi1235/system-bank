package com.banksystem.transaction.api.forensics;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.GatewayUser;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.common.security.SecurityHeaders;
import com.banksystem.common.security.UserContext;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.ExecuteAdjustmentRemediationRequest;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.ExecuteHoldRemediationRequest;
import com.banksystem.transaction.api.dto.RemediationProposalDtos.CreateRemediationProposalRequest;
import com.banksystem.transaction.api.dto.RemediationProposalDtos.RemediationProposalResponse;
import com.banksystem.transaction.application.forensics.RemediationProposalCommandService;
import com.banksystem.transaction.domain.forensics.AdjustmentDirection;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/forensics/remediation")
@RequirePermission(SecurityHeaders.PERM_FORENSICS_CASE_REVIEW)
public class AdminForensicRemediationExecutionController {

  private final RemediationProposalCommandService commandService;

  public AdminForensicRemediationExecutionController(
      RemediationProposalCommandService commandService) {
    this.commandService = commandService;
  }

  @PostMapping("/adjustment")
  public ApiResponse<RemediationProposalResponse> executeAdjustment(
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody ExecuteAdjustmentRemediationRequest request) {
    GatewayUser actor = UserContext.requireUser();

    // Route legacy API calls safely into the Proposal Command Service Pipeline!
    CreateRemediationProposalRequest createReq = new CreateRemediationProposalRequest(
        request.caseId(),
        request.transactionId(),
        request.targetAccountId(),
        AdjustmentDirection.CREDIT,
        request.amount(),
        "VND",
        request.reason() != null ? request.reason() : "Remediation Adjustment");

    RemediationProposalResponse draft = commandService.createDraft(createReq, actor.userId());
    RemediationProposalResponse submitted = commandService.submit(UUID.fromString(draft.id()), actor.userId(), draft.version());
    return ApiResponse.ok(submitted);
  }

  @PostMapping("/hold")
  public ApiResponse<RemediationProposalResponse> executeHold(
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody ExecuteHoldRemediationRequest request) {
    GatewayUser actor = UserContext.requireUser();

    CreateRemediationProposalRequest createReq = new CreateRemediationProposalRequest(
        request.caseId(),
        null,
        request.targetAccountId(),
        AdjustmentDirection.DEBIT,
        request.amount(),
        "VND",
        request.reason() != null ? request.reason() : "Remediation Hold");

    RemediationProposalResponse draft = commandService.createDraft(createReq, actor.userId());
    RemediationProposalResponse submitted = commandService.submit(UUID.fromString(draft.id()), actor.userId(), draft.version());
    return ApiResponse.ok(submitted);
  }
}
