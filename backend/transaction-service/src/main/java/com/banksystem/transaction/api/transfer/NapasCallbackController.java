package com.banksystem.transaction.api.transfer;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.transaction.api.dto.NapasCallbackDtos.PaymentCallbackRequest;
import com.banksystem.transaction.api.dto.TransferDtos.TransferResponse;
import com.banksystem.transaction.application.mapper.TransferMapper;
import com.banksystem.transaction.application.transfer.NapasResolutionService;
import com.banksystem.transaction.infrastructure.napas.NapasSwitchClient.NapasPaymentResponse;
import com.banksystem.transaction.infrastructure.napas.NapasSwitchClient.ProviderOutcome;
import com.banksystem.transaction.infrastructure.napas.NapasWebhookVerifier;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/callbacks/napas")
public class NapasCallbackController {

  private final NapasWebhookVerifier verifier;
  private final NapasResolutionService resolutionService;
  private final TransferMapper mapper;

  public NapasCallbackController(
      NapasWebhookVerifier verifier,
      NapasResolutionService resolutionService,
      TransferMapper mapper) {
    this.verifier = verifier;
    this.resolutionService = resolutionService;
    this.mapper = mapper;
  }

  @PostMapping("/payments")
  public ApiResponse<TransferResponse> paymentCallback(
      @RequestHeader("X-NAPAS-Timestamp") String timestampHeader,
      @RequestHeader("X-NAPAS-Signature") String signatureHeader,
      @Valid @RequestBody PaymentCallbackRequest request) {
    long timestamp = verifier.requireTimestamp(timestampHeader);
    verifier.verify(timestamp, signatureHeader, request.signingPayload(timestamp));
    ProviderOutcome outcome = parseOutcome(request.status());
    NapasPaymentResponse response = new NapasPaymentResponse(
        request.napasRefId(), outcome, request.responseCode(), request.responseMessage());
    return ApiResponse.ok(mapper.toResponse(resolutionService.applyCallback(
        request.clientRequestId(), request.napasRefId(), response)));
  }

  private ProviderOutcome parseOutcome(String status) {
    try {
      return ProviderOutcome.valueOf(status.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      return ProviderOutcome.UNKNOWN;
    }
  }
}
