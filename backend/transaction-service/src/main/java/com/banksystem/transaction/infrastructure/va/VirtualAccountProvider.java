package com.banksystem.transaction.infrastructure.va;

import com.banksystem.transaction.domain.virtualaccount.VirtualAccountMode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public interface VirtualAccountProvider {

  String getProviderCode();

  ProvisionedVirtualAccount provision(VirtualAccountProvisionRequest request);

  void close(VirtualAccountCloseRequest request);

  VerifiedInboundPayment verifyWebhook(String rawPayload, Map<String, String> headers);

  record VirtualAccountProvisionRequest(
      UUID organizationId,
      String bankBin,
      UUID parentAccountId,
      VirtualAccountMode mode,
      String customerReference,
      Instant expiresAt
  ) {}

  record ProvisionedVirtualAccount(
      String provider,
      String bankBin,
      String accountNumber,
      String vietQrUrl
  ) {}

  record VirtualAccountCloseRequest(
      String provider,
      String bankBin,
      String accountNumber
  ) {}

  record VerifiedInboundPayment(
      boolean valid,
      String provider,
      String providerTransactionId,
      String virtualAccountNumber,
      String bankBin,
      BigDecimal amount,
      String currency,
      String senderAccount,
      String senderBankBin,
      String senderName,
      String referenceContent,
      String rawPayloadHash,
      String rawPayload,
      String errorMessage
  ) {}
}
