package com.banksystem.transaction.api.forensics;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.RequireInternalApiKey;
import com.banksystem.transaction.infrastructure.inbox.RemediationResultInboxConsumer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/forensics/remediation")
@RequireInternalApiKey
public class InternalRemediationInboxController {

  private final RemediationResultInboxConsumer resultConsumer;

  public InternalRemediationInboxController(RemediationResultInboxConsumer resultConsumer) {
    this.resultConsumer = resultConsumer;
  }

  public record RemediationPostedEventRequest(
      @NotNull UUID eventId,
      @NotNull UUID proposalId,
      @NotNull UUID caseId,
      int cycle,
      String referenceId,
      String targetAccountId) {}

  @PostMapping("/result-inbox")
  public ApiResponse<Boolean> processResultInbox(
      @Valid @RequestBody RemediationPostedEventRequest req) {
    resultConsumer.consumeRemediationPostedEvent(
        req.eventId(),
        req.proposalId(),
        req.caseId(),
        req.cycle(),
        req.referenceId(),
        req.targetAccountId());
    return ApiResponse.ok(true);
  }
}
