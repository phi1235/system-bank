package com.banksystem.account.infrastructure.feign;
import com.banksystem.account.application.account.*;
import com.banksystem.account.application.card.*;
import com.banksystem.account.application.deposit.*;
import com.banksystem.account.application.ledger.*;
import com.banksystem.account.domain.account.*;
import com.banksystem.account.domain.card.*;
import com.banksystem.account.domain.deposit.*;
import com.banksystem.account.domain.ledger.*;
import com.banksystem.account.api.dto.*;

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
