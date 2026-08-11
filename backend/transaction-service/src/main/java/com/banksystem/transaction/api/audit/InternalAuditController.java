package com.banksystem.transaction.api.audit;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.RequireInternalApiKey;
import com.banksystem.transaction.api.dto.TransferDtos.AuditResponse;
import com.banksystem.transaction.api.dto.TransferDtos.CreateAuditLogRequest;
import com.banksystem.transaction.application.audit.AuditCommandService;
import com.banksystem.transaction.application.audit.AuditCommandService.CreateAuditLogCommand;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/audit-logs")
@RequireInternalApiKey
public class InternalAuditController {

  private final AuditCommandService auditCommandService;

  public InternalAuditController(AuditCommandService auditCommandService) {
    this.auditCommandService = auditCommandService;
  }

  @PostMapping
  public ApiResponse<AuditResponse> create(
      @Valid @RequestBody CreateAuditLogRequest request) {
    CreateAuditLogCommand command = new CreateAuditLogCommand(
        request.actorUserId(),
        request.action(),
        request.resourceType(),
        request.resourceId(),
        request.ip(),
        request.metadata());
    return ApiResponse.ok(auditCommandService.recordInternalAudit(command));
  }
}
