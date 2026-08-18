package com.banksystem.transaction.application.forensics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class ForensicArtifactCodec {
  private final ObjectMapper objectMapper;

  public ForensicArtifactCodec(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
  }

  public byte[] write(Object value) {
    try {
      return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Cannot serialize forensic artifact", exception);
    }
  }

  public JsonNode tree(Object value) { return objectMapper.valueToTree(value); }

  public JsonNode read(byte[] content) {
    try {
      return objectMapper.readTree(content);
    } catch (Exception exception) {
      throw new IllegalStateException("Cannot parse forensic artifact", exception);
    }
  }

  public String sha256(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }

  public String sha256(String value) { return sha256(value.getBytes(StandardCharsets.UTF_8)); }
}
