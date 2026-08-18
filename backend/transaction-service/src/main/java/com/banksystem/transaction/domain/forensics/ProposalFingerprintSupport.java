package com.banksystem.transaction.domain.forensics;

import com.banksystem.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ProposalFingerprintSupport {
  private final ObjectMapper objectMapper;

  public ProposalFingerprintSupport() {
    this.objectMapper = new ObjectMapper();
    this.objectMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
  }

  public String calculateCanonicalHash(CanonicalProposalPayload payload) {
    if (payload == null) {
      throw new BusinessException("PAYLOAD_REQUIRED", "Payload cannot be null for fingerprinting", HttpStatus.BAD_REQUEST);
    }
    try {
      String canonicalJson = objectMapper.writeValueAsString(payload);
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashBytes = digest.digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
      return bytesToHex(hashBytes);
    } catch (JsonProcessingException e) {
      throw new BusinessException("CANONICAL_SERIALIZATION_FAILED", "Failed to serialize canonical payload", HttpStatus.INTERNAL_SERVER_ERROR);
    } catch (NoSuchAlgorithmException e) {
      throw new BusinessException("HASH_ALGORITHM_UNAVAILABLE", "SHA-256 algorithm unavailable", HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  public String calculateCanonicalHash(RemediationProposalEntity proposal) {
    CanonicalProposalPayload payload = CanonicalProposalPayload.fromEntity(proposal);
    return calculateCanonicalHash(payload);
  }

  public void verifyFingerprint(RemediationProposalEntity proposal, String submittedHash) {
    String currentCalculatedHash = calculateCanonicalHash(proposal);
    if (!currentCalculatedHash.equalsIgnoreCase(submittedHash)) {
      throw new BusinessException(
          "PROPOSAL_TAMPERED_CONFLICT",
          "Proposal payload hash mismatch! Submitted hash: " + submittedHash + ", Calculated hash: " + currentCalculatedHash,
          HttpStatus.CONFLICT);
    }
  }

  private String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    for (byte b : bytes) {
      sb.append(String.format("%02X", b));
    }
    return sb.toString();
  }
}
