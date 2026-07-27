package com.banksystem.account.infrastructure.feign;

import com.banksystem.common.api.ApiResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "TRANSACTION-SERVICE", url = "${TRANSACTION_SERVICE_URL:}")
public interface AuditClient {

  record CreateAuditLogRequest(
      UUID actorUserId,
      String action,
      String resourceType,
      String resourceId,
      String ip,
      String metadata) {}

  @PostMapping("/internal/audit-logs")
  ApiResponse<Object> createAuditLog(
      @RequestBody CreateAuditLogRequest request,
      @RequestHeader("X-Internal-Api-Key") String apiKey);
}
