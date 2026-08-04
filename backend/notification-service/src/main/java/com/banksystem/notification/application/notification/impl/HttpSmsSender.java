package com.banksystem.notification.application.notification.impl;
import com.banksystem.notification.application.notification.*;
import com.banksystem.notification.domain.notification.*;
import com.banksystem.notification.domain.event.*;
import com.banksystem.notification.api.dto.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * HTTP SMS gateway adapter. Point {@code SMS_BASE_URL} at a real provider or sandbox proxy.
 * Optional API key via {@code SMS_API_KEY} (env only).
 */
@Component
@ConditionalOnProperty(name = "bank.sms.provider", havingValue = "http")
public class HttpSmsSender implements SmsSender {

  private static final Logger log = LoggerFactory.getLogger(HttpSmsSender.class);

  private final String baseUrl;
  private final String apiKey;
  private final String from;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  public HttpSmsSender(
      @Value("${bank.sms.base-url}") String baseUrl,
      @Value("${bank.sms.api-key:}") String apiKey,
      @Value("${bank.sms.from:}") String from,
      ObjectMapper objectMapper) {
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalStateException("bank.sms.base-url (SMS_BASE_URL) must be set when provider=http");
    }
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.apiKey = apiKey == null ? "" : apiKey;
    this.from = from == null ? "" : from;
    this.objectMapper = objectMapper;
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  }

  @Override
  public void send(String to, String body) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("to", to);
      payload.put("body", body == null ? "" : body);
      if (!from.isBlank()) {
        payload.put("from", from);
      }
      byte[] json = objectMapper.writeValueAsBytes(payload);
      HttpRequest.Builder b =
          HttpRequest.newBuilder()
              .uri(URI.create(baseUrl + "/v1/sms/send"))
              .timeout(Duration.ofSeconds(15))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofByteArray(json));
      if (!apiKey.isBlank()) {
        b.header("X-API-Key", apiKey);
        b.header("Authorization", "Bearer " + apiKey);
      }
      HttpResponse<String> resp = httpClient.send(b.build(), HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
        log.info("HTTP_SMS sent to={} status={}", to, resp.statusCode());
        return;
      }
      throw new IllegalStateException(
          "SMS gateway HTTP " + resp.statusCode() + ": " + truncate(resp.body()));
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("SMS gateway call failed: " + e.getMessage(), e);
    }
  }

  private static String truncate(String s) {
    if (s == null) {
      return "";
    }
    return s.length() > 200 ? s.substring(0, 200) + "..." : s;
  }
}
