package com.banksystem.transaction.api.forensics;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.common.security.SecurityHeaders;
import com.banksystem.transaction.api.dto.ForensicDtos.CausalGraphResponse;
import com.banksystem.transaction.application.forensics.ForensicInvestigationQueryService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/forensics/causal-graph")
@RequirePermission(SecurityHeaders.PERM_FORENSICS_VIEW)
public class AdminForensicGraphController {
  private final ForensicInvestigationQueryService service;

  public AdminForensicGraphController(ForensicInvestigationQueryService service) {
    this.service = service;
  }

  @GetMapping("/{transactionId}")
  public ApiResponse<CausalGraphResponse> get(@PathVariable UUID transactionId) {
    return ApiResponse.ok(service.causalGraph(transactionId));
  }
}
