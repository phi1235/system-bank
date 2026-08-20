package com.banksystem.corporate.application.signature;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Fail-closed provider used until a production CA adapter is explicitly configured. */
@Component
@ConditionalOnProperty(name = "bank.ca.provider", havingValue = "disabled", matchIfMissing = true)
public class DisabledCaSignatureProvider implements TransactionSignatureProvider {
  @Override
  public SignatureChallengeResult createChallenge(String payloadHash) {
    return new SignatureChallengeResult("", payloadHash, 0);
  }

  @Override
  public SignatureVerificationResult verifySignature(String nonce, String token, String payloadHash) {
    return new SignatureVerificationResult(false, null, null, null, null,
        "Digital signature provider is not configured");
  }
}
