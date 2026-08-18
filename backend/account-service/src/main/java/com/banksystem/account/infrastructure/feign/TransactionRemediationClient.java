package com.banksystem.account.infrastructure.feign;

import com.banksystem.common.api.ApiResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
    name = "TRANSACTION-SERVICE",
    contextId = "transactionRemediationClient",
    url = "${bank.feign.transaction-url:http://localhost:8082}")
public interface TransactionRemediationClient {

  public record RemediationPostedEventRequest(
      UUID eventId,
      UUID proposalId,
      UUID caseId,
      int cycle,
      String referenceId,
      String targetAccountId) {}

  @PostMapping("/internal/forensics/remediation/result-inbox")
  ApiResponse<Boolean> processResultInbox(
      @RequestBody RemediationPostedEventRequest request,
      @RequestHeader("X-Internal-Api-Key") String apiKey);
}
