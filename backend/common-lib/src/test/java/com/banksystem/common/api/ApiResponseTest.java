package com.banksystem.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

class ApiResponseTest {

  @Test
  void serializesSuccessEnvelope() throws Exception {
    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    ApiResponse<String> response = ApiResponse.ok("hello", "corr-1");
    String json = mapper.writeValueAsString(response);
    assertTrue(json.contains("\"success\":true"));
    assertTrue(json.contains("hello"));
    assertTrue(json.contains("corr-1"));
    ApiResponse<?> parsed = mapper.readValue(json, ApiResponse.class);
    assertEquals(true, parsed.success());
  }
}
