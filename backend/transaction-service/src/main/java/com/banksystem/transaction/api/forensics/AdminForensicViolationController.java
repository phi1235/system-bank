package com.banksystem.transaction.api.forensics;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.api.PageResponse;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.common.security.SecurityHeaders;
import com.banksystem.common.security.UserContext;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.AcknowledgeForensicViolationRequest;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.ForensicFindingResponse;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.ForensicViolationFilterRequest;
import com.banksystem.transaction.api.dto.ForensicCaseDtos.ResolveForensicViolationRequest;
import com.banksystem.transaction.application.forensics.ForensicFindingService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/forensics/violations")
public class AdminForensicViolationController {
  private final ForensicFindingService service;

  public AdminForensicViolationController(ForensicFindingService service) { this.service = service; }

  @GetMapping
  @RequirePermission(SecurityHeaders.PERM_FORENSICS_VIEW)
  public ApiResponse<PageResponse<ForensicFindingResponse>> search(
      @Valid @ModelAttribute ForensicViolationFilterRequest request) {
    return ApiResponse.ok(service.searchViolations(
        request.disposition(), request.severity(), request.ruleCode(), request.transactionId(),
        request.since(), request.page() == null ? 0 : request.page(),
        request.size() == null ? 20 : request.size()));
  }

  @GetMapping("/{id}")
  @RequirePermission(SecurityHeaders.PERM_FORENSICS_VIEW)
  public ApiResponse<ForensicFindingResponse> get(@PathVariable UUID id) {
    return ApiResponse.ok(service.get(id));
  }

  @PostMapping("/{id}/acknowledge")
  @RequirePermission(SecurityHeaders.PERM_FORENSICS_CASE_REVIEW)
  public ApiResponse<ForensicFindingResponse> acknowledge(
      @PathVariable UUID id, @Valid @RequestBody AcknowledgeForensicViolationRequest request) {
    return ApiResponse.ok(service.acknowledge(
        id, UserContext.requireUser().userId(), request.expectedVersion(), request.note()));
  }

  @PostMapping("/{id}/resolve")
  @RequirePermission(SecurityHeaders.PERM_FORENSICS_CASE_REVIEW)
  public ApiResponse<ForensicFindingResponse> resolve(
      @PathVariable UUID id, @Valid @RequestBody ResolveForensicViolationRequest request) {
    return ApiResponse.ok(service.resolve(
        id, UserContext.requireUser().userId(), request.expectedVersion(),
        request.reason(), request.evidence()));
  }
}
