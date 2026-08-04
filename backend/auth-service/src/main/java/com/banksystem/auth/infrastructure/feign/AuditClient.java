package com.banksystem.auth.infrastructure.feign;
import static com.banksystem.auth.api.dto.AuthDtos.*;
import static com.banksystem.auth.api.dto.PasswordResetDtos.*;
import static com.banksystem.auth.api.dto.RbacDtos.*;
import com.banksystem.auth.application.auth.*;
import com.banksystem.auth.application.rbac.*;
import com.banksystem.auth.domain.auth.*;
import com.banksystem.auth.domain.rbac.*;
import com.banksystem.auth.api.dto.*;

import com.banksystem.common.api.ApiResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "TRANSACTION-SERVICE", url = "${bank.feign.transaction-url}")
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
