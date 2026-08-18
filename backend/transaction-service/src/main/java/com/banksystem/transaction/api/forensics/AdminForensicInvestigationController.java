package com.banksystem.transaction.api.forensics;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.api.PageResponse;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.common.security.SecurityHeaders;
import com.banksystem.transaction.api.dto.ForensicDtos.ForensicInvestigationFilterRequest;
import com.banksystem.transaction.api.dto.ForensicDtos.EvidenceTimelineFilterRequest;
import com.banksystem.transaction.api.dto.ForensicDtos.InvestigationDetailResponse;
import com.banksystem.transaction.api.dto.ForensicDtos.InvestigationItemResponse;
import com.banksystem.transaction.api.dto.ForensicDtos.TimelineEvidenceResponse;
import com.banksystem.transaction.api.dto.ForensicDtos.TemporalInvestigationStateResponse;
import com.banksystem.transaction.application.forensics.ForensicInvestigationQueryService;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

/** Back-office HTTP adapter for the read-only forensic investigation read model. */
@RestController
@RequestMapping("/api/v1/admin/forensics/investigations")
@RequirePermission(SecurityHeaders.PERM_FORENSICS_VIEW)
public class AdminForensicInvestigationController {

  private final ForensicInvestigationQueryService service;

  public AdminForensicInvestigationController(ForensicInvestigationQueryService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<PageResponse<InvestigationItemResponse>> search(
      @Valid @ModelAttribute ForensicInvestigationFilterRequest request) {
    return ApiResponse.ok(service.search(ForensicRequestMapper.toQuery(request)));
  }

  @PostMapping("/findByCondition")
  public ApiResponse<PageResponse<InvestigationItemResponse>> findByCondition(
      @Valid @RequestBody ForensicInvestigationFilterRequest request) {
    return ApiResponse.ok(service.search(ForensicRequestMapper.toQuery(request)));
  }

  @GetMapping("/{transactionId}")
  public ApiResponse<InvestigationDetailResponse> get(@PathVariable UUID transactionId) {
    return ApiResponse.ok(service.get(transactionId));
  }

  @GetMapping("/{transactionId}/timeline")
  public ApiResponse<PageResponse<TimelineEvidenceResponse>> timeline(
      @PathVariable UUID transactionId,
      @Valid @ModelAttribute EvidenceTimelineFilterRequest request) {
    return ApiResponse.ok(service.timeline(transactionId, ForensicRequestMapper.toQuery(request)));
  }

  @GetMapping("/{transactionId}/temporal-state")
  public ApiResponse<TemporalInvestigationStateResponse> temporalState(
      @PathVariable UUID transactionId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant at) {
    return ApiResponse.ok(service.temporalState(transactionId, at));
  }

}
