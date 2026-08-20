package com.banksystem.transaction.api.forensics;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.api.PageResponse;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.common.security.SecurityHeaders;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.ApproveForensicResolutionRequest;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.AssignForensicCaseRequest;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.ConfirmRootCauseRequest;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.CreateForensicCaseRequest;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.CreateForensicFindingRequest;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.ForensicCaseDetailResponse;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.ForensicCaseFilterRequest;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.ForensicCaseHistoryResponse;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.ForensicCaseResponse;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.ForensicFindingResponse;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.RecordRemediationRequest;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.RejectForensicResolutionRequest;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.ReopenForensicCaseRequest;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.SubmitForensicCaseRequest;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.VerifyReplayRequest;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.VersionedForensicCaseRequest;
import com.banksystem.transaction.application.forensics.ForensicCaseCommandService;
import com.banksystem.transaction.application.forensics.ForensicCaseQueryService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/forensics/cases")
@RequirePermission(SecurityHeaders.PERM_FORENSICS_VIEW)
public class AdminForensicCaseController {
  private final ForensicCaseQueryService queryService;
  private final ForensicCaseCommandService commandService;
  private final ForensicCaseRequestMapper requestMapper;

  public AdminForensicCaseController(
      ForensicCaseQueryService queryService,
      ForensicCaseCommandService commandService,
      ForensicCaseRequestMapper requestMapper) {
    this.queryService = queryService;
    this.commandService = commandService;
    this.requestMapper = requestMapper;
  }

  @GetMapping
  public ApiResponse<PageResponse<ForensicCaseResponse>> search(
      @Valid @ModelAttribute ForensicCaseFilterRequest request) {
    return ApiResponse.ok(queryService.search(requestMapper.toQuery(request)));
  }

  @PostMapping("/findByCondition")
  public ApiResponse<PageResponse<ForensicCaseResponse>> findByCondition(
      @Valid @RequestBody ForensicCaseFilterRequest request) {
    return ApiResponse.ok(queryService.search(requestMapper.toQuery(request)));
  }

  @GetMapping("/{id}")
  public ApiResponse<ForensicCaseDetailResponse> get(@PathVariable UUID id) {
    return ApiResponse.ok(queryService.get(id));
  }

  @PostMapping
  @RequirePermission(SecurityHeaders.PERM_FORENSICS_CASE_REVIEW)
  public ApiResponse<ForensicCaseResponse> create(
      @Valid @RequestBody CreateForensicCaseRequest request) {
    return ApiResponse.ok(commandService.create(request));
  }

  @PostMapping("/{id}/assign")
  @RequirePermission(SecurityHeaders.PERM_FORENSICS_CASE_REVIEW)
  public ApiResponse<ForensicCaseResponse> assign(
      @PathVariable UUID id, @Valid @RequestBody AssignForensicCaseRequest request) {
    return ApiResponse.ok(commandService.assign(id, request));
  }

  @PostMapping("/{id}/start")
  @RequirePermission(SecurityHeaders.PERM_FORENSICS_CASE_REVIEW)
  public ApiResponse<ForensicCaseResponse> start(
      @PathVariable UUID id, @Valid @RequestBody VersionedForensicCaseRequest request) {
    return ApiResponse.ok(commandService.start(id, request));
  }

  @PostMapping("/{id}/confirm-root-cause")
  @RequirePermission(SecurityHeaders.PERM_FORENSICS_CASE_REVIEW)
  public ApiResponse<ForensicCaseResponse> confirmRootCause(
      @PathVariable UUID id, @Valid @RequestBody ConfirmRootCauseRequest request) {
    return ApiResponse.ok(commandService.confirmRootCause(id, request));
  }

  @PostMapping("/{id}/verify-replay")
  @RequirePermission(SecurityHeaders.PERM_FORENSICS_CASE_REVIEW)
  public ApiResponse<ForensicCaseResponse> verifyReplay(
      @PathVariable UUID id, @Valid @RequestBody VerifyReplayRequest request) {
    return ApiResponse.ok(commandService.verifyReplay(id, request));
  }

  @PostMapping("/{id}/submit")
  @RequirePermission(SecurityHeaders.PERM_FORENSICS_CASE_REVIEW)
  public ApiResponse<ForensicCaseResponse> submit(
      @PathVariable UUID id, @Valid @RequestBody SubmitForensicCaseRequest request) {
    return ApiResponse.ok(commandService.submit(id, request));
  }

  @PostMapping("/{id}/approve-resolution")
  @RequirePermission(SecurityHeaders.PERM_FORENSICS_CASE_REVIEW)
  public ApiResponse<ForensicCaseResponse> approve(
      @PathVariable UUID id, @Valid @RequestBody ApproveForensicResolutionRequest request) {
    return ApiResponse.ok(commandService.approve(id, request));
  }

  @PostMapping("/{id}/reject-resolution")
  @RequirePermission(SecurityHeaders.PERM_FORENSICS_CASE_REVIEW)
  public ApiResponse<ForensicCaseResponse> reject(
      @PathVariable UUID id, @Valid @RequestBody RejectForensicResolutionRequest request) {
    return ApiResponse.ok(commandService.reject(id, request));
  }

  @PostMapping("/{id}/reopen")
  @RequirePermission(SecurityHeaders.PERM_FORENSICS_ADMIN)
  public ApiResponse<ForensicCaseResponse> reopen(
      @PathVariable UUID id, @Valid @RequestBody ReopenForensicCaseRequest request) {
    return ApiResponse.ok(commandService.reopen(id, request));
  }

  @PostMapping("/{id}/findings")
  @RequirePermission(SecurityHeaders.PERM_FORENSICS_CASE_REVIEW)
  public ApiResponse<ForensicFindingResponse> addFinding(
      @PathVariable UUID id, @Valid @RequestBody CreateForensicFindingRequest request) {
    return ApiResponse.ok(commandService.addFinding(id, request));
  }

  @PostMapping("/{id}/remediation")
  @RequirePermission(SecurityHeaders.PERM_FORENSICS_CASE_REVIEW)
  public ApiResponse<ForensicCaseResponse> recordRemediation(
      @PathVariable UUID id, @Valid @RequestBody RecordRemediationRequest request) {
    return ApiResponse.ok(commandService.recordRemediation(id, request));
  }
  @GetMapping("/{id}/history")
  @RequirePermission(SecurityHeaders.PERM_FORENSICS_AUDIT_VIEW)
  public ApiResponse<PageResponse<ForensicCaseHistoryResponse>> history(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.ok(queryService.history(id, page, size));
  }
}
