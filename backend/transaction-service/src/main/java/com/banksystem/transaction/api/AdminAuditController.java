package com.banksystem.transaction.api;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.transaction.api.dto.TransferDtos.AdminAuditFilterRequest;
import com.banksystem.transaction.api.dto.TransferDtos.AuditResponse;
import com.banksystem.transaction.application.AuditAdminService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
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
  public ApiResponse<?> list(@Valid @ModelAttribute AdminAuditFilterRequest req) {
    return ApiResponse.ok(service.list(req));
  }

  @GetMapping("/{id}")
  public ApiResponse<AuditResponse> get(@PathVariable UUID id) {
    return ApiResponse.ok(service.get(id));
  }
}
