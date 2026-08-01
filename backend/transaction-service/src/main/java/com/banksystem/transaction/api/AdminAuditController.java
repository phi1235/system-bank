package com.banksystem.transaction.api;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.api.PageResponse;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.transaction.api.dto.TransferDtos.AuditResponse;
import com.banksystem.transaction.application.AuditAdminService;
import com.banksystem.transaction.application.query.AuditListQuery;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff audit log inspect. HTTP + permission only; rules in {@link AuditAdminService}.
 * Gateway: {@code /api/v1/admin/**} → TRANSACTION-SERVICE.
 */
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@RequirePermission("audit:list:view")
public class AdminAuditController {

  private final AuditAdminService service;

  public AdminAuditController(AuditAdminService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<?> list(
      @RequestParam(required = false) String action,
      @RequestParam(required = false) String resourceType,
      @RequestParam(required = false) String actorUserId,
      @RequestParam(required = false) String resourceId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false, defaultValue = "false") boolean noCount) {
    AuditListQuery query = AuditListQuery.of(action, resourceType, actorUserId, resourceId, from, to, page, size);
    if (noCount) {
      return ApiResponse.ok(service.listSlice(query));
    }
    return ApiResponse.ok(service.list(query));
  }

  @GetMapping("/{id}")
  public ApiResponse<AuditResponse> get(@PathVariable UUID id) {
    return ApiResponse.ok(service.get(id));
  }
}
