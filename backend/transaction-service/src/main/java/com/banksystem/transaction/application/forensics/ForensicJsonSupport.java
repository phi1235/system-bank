package com.banksystem.transaction.application.forensics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class ForensicJsonSupport {
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
  private final ObjectMapper objectMapper;

  ForensicJsonSupport(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
  }

  String serialize(Map<String, Object> value) {
    try {
      return objectMapper.writeValueAsString(value == null ? Map.of() : value);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Invalid structured forensic evidence", exception);
    }
  }

  String serialize(Object value) {
    try {
      return objectMapper.writeValueAsString(value == null ? Map.of() : value);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Invalid structured forensic data", exception);
    }
  }

  Object deserializeAny(String value) {
    try {
      return objectMapper.readValue(value == null ? "null" : value, Object.class);
    } catch (JsonProcessingException exception) {
      return null;
    }
  }

  Map<String, Object> deserialize(String value) {
    try {
      return objectMapper.readValue(value == null ? "{}" : value, MAP_TYPE);
    } catch (JsonProcessingException exception) {
      return Map.of("status", "CORRUPTED");
    }
  }

  String sha256(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }
}
