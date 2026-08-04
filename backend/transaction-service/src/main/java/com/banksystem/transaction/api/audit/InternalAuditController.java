package com.banksystem.transaction.api.audit;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.SecurityHeaders;
import com.banksystem.transaction.api.dto.TransferDtos.AuditResponse;
import com.banksystem.transaction.application.audit.AuditCommandService;
import com.banksystem.transaction.application.audit.AuditCommandService.CreateAuditLogCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for internal audit log creation.
 * Follows Clean Architecture: Handles HTTP routing & request parsing only; delegates all logic to Application Service.
 */
@RestController
@RequestMapping("/internal/audit-logs")
public class InternalAuditController {

  public record CreateAuditLogRequest(
      UUID actorUserId,
      @NotBlank String action,
      String resourceType,
      String resourceId,
      String ip,
      String metadata) {}

  private final AuditCommandService auditCommandService;

  public InternalAuditController(AuditCommandService auditCommandService) {
    this.auditCommandService = auditCommandService;
  }

  @PostMapping
  public ApiResponse<AuditResponse> create(
      @RequestHeader(value = SecurityHeaders.INTERNAL_API_KEY, required = false) String key,
      @Valid @RequestBody CreateAuditLogRequest request) {
    CreateAuditLogCommand command = new CreateAuditLogCommand(
        request.actorUserId(),
        request.action(),
        request.resourceType(),
        request.resourceId(),
        request.ip(),
        request.metadata());
    AuditResponse response = auditCommandService.recordInternalAudit(key, command);
    return ApiResponse.ok(response);
  }
}
