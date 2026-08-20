package com.banksystem.corporate.application.signature;

public interface TransactionSignatureProvider {

  record SignatureChallengeResult(String nonce, String payloadHash, long ttlSeconds) {}
  record SignatureVerificationResult(
      boolean valid,
      String certificateSubject,
      String certificateIssuer,
      String certificateSerial,
      String signatureAlgorithm,
      String failureReason
  ) {}

  SignatureChallengeResult createChallenge(String payloadHash);

  SignatureVerificationResult verifySignature(
      String challengeNonce,
      String signatureToken,
      String payloadHash);
}
