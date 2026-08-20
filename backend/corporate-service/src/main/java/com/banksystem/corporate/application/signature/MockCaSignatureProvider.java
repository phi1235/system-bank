package com.banksystem.corporate.application.signature;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "bank.ca.provider", havingValue = "mock")
public class MockCaSignatureProvider implements TransactionSignatureProvider {

  @Override
  public SignatureChallengeResult createChallenge(String payloadHash) {
    String nonce = UUID.randomUUID().toString().replace("-", "");
    return new SignatureChallengeResult(nonce, payloadHash, 300);
  }

  @Override
  public SignatureVerificationResult verifySignature(
      String challengeNonce,
      String signatureToken,
      String payloadHash) {
    if (signatureToken == null || signatureToken.isBlank()) {
      return new SignatureVerificationResult(
          false, null, null, null, null, "Signature token is required");
    }

    boolean valid = signatureToken.startsWith("CA_VALID_") || signatureToken.startsWith("SIGN_") || signatureToken.length() >= 6;
    if (!valid) {
      return new SignatureVerificationResult(
          false, null, null, null, null, "Invalid CA digital signature");
    }

    return new SignatureVerificationResult(
        true,
        "CN=Corporate Signer, O=Bank Enterprise Corp, C=VN",
        "CN=VNPT-CA Global Root, O=VNPT, C=VN",
        "SN-" + Math.abs(signatureToken.hashCode()),
        "SHA256withRSA",
        null);
  }
}
