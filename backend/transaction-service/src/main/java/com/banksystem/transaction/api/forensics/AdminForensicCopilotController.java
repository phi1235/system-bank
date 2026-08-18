package com.banksystem.transaction.api.forensics;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.common.security.SecurityHeaders;
import com.banksystem.common.security.UserContext;
import com.banksystem.transaction.api.dto.ForensicCopilotDtos.CopilotAnswerResponse;
import com.banksystem.transaction.api.dto.ForensicCopilotDtos.CopilotMessageRequest;
import com.banksystem.transaction.api.dto.ForensicCopilotDtos.CopilotProviderHealthResponse;
import com.banksystem.transaction.api.dto.ForensicCopilotDtos.CopilotSessionResponse;
import com.banksystem.transaction.api.dto.ForensicCopilotDtos.CreateCopilotSessionRequest;
import com.banksystem.transaction.application.forensics.ForensicCopilotService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/forensics/copilot")
public class AdminForensicCopilotController {
  private final ForensicCopilotService service;
  public AdminForensicCopilotController(ForensicCopilotService service) { this.service = service; }
  @PostMapping("/sessions") @RequirePermission(SecurityHeaders.PERM_FORENSICS_COPILOT_USE)
  public ApiResponse<CopilotSessionResponse> create(@Valid @RequestBody CreateCopilotSessionRequest request) { return ApiResponse.ok(service.create(request.transactionId(), request.caseId(), UserContext.requireUser())); }
  @PostMapping("/sessions/{sessionId}/messages") @RequirePermission(SecurityHeaders.PERM_FORENSICS_COPILOT_USE)
  public ApiResponse<CopilotAnswerResponse> ask(@PathVariable UUID sessionId, @Valid @RequestBody CopilotMessageRequest request) { return ApiResponse.ok(service.ask(sessionId, request.question(), UserContext.requireUser())); }
  @GetMapping("/providers/health") @RequirePermission(SecurityHeaders.PERM_FORENSICS_ADMIN)
  public ApiResponse<CopilotProviderHealthResponse> health() { return ApiResponse.ok(service.health()); }
}
