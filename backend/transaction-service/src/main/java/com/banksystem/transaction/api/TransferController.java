package com.banksystem.transaction.api;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.api.PageResponse;
import com.banksystem.transaction.api.dto.TransferDtos.AuditResponse;
import com.banksystem.transaction.api.dto.TransferDtos.TransferRequest;
import com.banksystem.transaction.api.dto.TransferDtos.TransferResponse;
import com.banksystem.transaction.application.TransferService;
import com.banksystem.transaction.config.UserContext;
import com.banksystem.transaction.domain.AuditLogEntity;
import com.banksystem.transaction.domain.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class TransferController {

  private final TransferService transferService;
  private final AuditLogRepository auditLogRepository;

  public TransferController(TransferService transferService, AuditLogRepository auditLogRepository) {
    this.transferService = transferService;
    this.auditLogRepository = auditLogRepository;
  }

  @PostMapping("/transactions/transfers")
  public ApiResponse<TransferResponse> transfer(
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody TransferRequest req,
      HttpServletRequest http) {
    return ApiResponse.ok(transferService.transfer(
        UserContext.requireUser(), idempotencyKey, req, clientIp(http)));
  }

  @GetMapping("/transactions/transfers")
  public ApiResponse<PageResponse<TransferResponse>> myTransfers(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.ok(transferService.myHistory(
        UserContext.requireUser().userId(), page, Math.min(size, 100)));
  }

  @GetMapping("/transactions/transfers/{id}")
  public ApiResponse<TransferResponse> get(@PathVariable UUID id) {
    return ApiResponse.ok(transferService.get(id, UserContext.requireUser()));
  }

  @GetMapping({"/admin/transfers", "/transactions/admin/transfers"})
  public ApiResponse<PageResponse<TransferResponse>> adminTransfers(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String status) {
    UserContext.requirePermission("transactions:list:view");
    return ApiResponse.ok(transferService.adminList(status, page, Math.min(size, 100)));
  }

  @GetMapping({"/admin/audit-logs", "/transactions/admin/audit-logs"})
  public ApiResponse<PageResponse<AuditResponse>> auditLogs(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    UserContext.requirePermission("audit:list:view");
    var p = auditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, Math.min(size, 100)));
    var items = p.getContent().stream().map(this::toAudit).toList();
    return ApiResponse.ok(new PageResponse<>(items, p.getNumber(), p.getSize(),
        p.getTotalElements(), p.getTotalPages()));
  }

  private AuditResponse toAudit(AuditLogEntity e) {
    return new AuditResponse(
        e.getId().toString(),
        e.getActorUserId() == null ? null : e.getActorUserId().toString(),
        e.getAction(),
        e.getResourceType(),
        e.getResourceId(),
        e.getIp(),
        e.getMetadata(),
        e.getCreatedAt()
    );
  }

  private String clientIp(HttpServletRequest request) {
    String xff = request.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
      return xff.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
