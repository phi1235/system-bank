package com.banksystem.transaction.infrastructure.forensics;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.application.forensics.ForensicAiProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HttpForensicAiProvider implements ForensicAiProvider {
  private final boolean enabled;
  private final String endpoint;
  private final String apiKey;
  private final String model;
  private final HttpClient client;
  private final Duration readTimeout;
  private final ObjectMapper objectMapper;

  public HttpForensicAiProvider(
      @Value("${bank.forensics.ai.enabled}") boolean enabled,
      @Value("${bank.forensics.ai.endpoint}") String endpoint,
      @Value("${bank.forensics.ai.api-key}") String apiKey,
      @Value("${bank.forensics.ai.model}") String model,
      @Value("${bank.forensics.ai.connect-timeout}") Duration connectTimeout,
      @Value("${bank.forensics.ai.read-timeout}") Duration readTimeout,
      ObjectMapper objectMapper) {
    this.enabled = enabled;
    this.endpoint = endpoint;
    this.apiKey = apiKey;
    this.model = model;
    this.client = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
    this.readTimeout = readTimeout;
    this.objectMapper = objectMapper;
  }

  @Override
  public ProviderHealth health() {
    boolean configured = enabled && !endpoint.isBlank() && !apiKey.isBlank() && !model.isBlank();
    return new ProviderHealth(enabled, configured, "OPENAI_COMPATIBLE", model,
        configured ? "READY" : enabled ? "MISCONFIGURED" : "DISABLED");
  }

  @Override
  @CircuitBreaker(name = "FORENSICS_AI")
  @Retry(name = "FORENSICS_AI")
  public String complete(String systemPrompt, String evidencePrompt) {
    if (!health().configured()) throw new BusinessException("AI_PROVIDER_UNAVAILABLE", "AI provider is not configured");
    try {
      byte[] body = objectMapper.writeValueAsBytes(Map.of(
          "model", model, "temperature", 0,
          "messages", List.of(Map.of("role", "system", "content", systemPrompt),
              Map.of("role", "user", "content", evidencePrompt))));
      HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(readTimeout)
          .header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new BusinessException("AI_PROVIDER_UNAVAILABLE", "AI provider request failed");
      }
      JsonNode root = objectMapper.readTree(response.body());
      String answer = root.path("choices").path(0).path("message").path("content").asText();
      if (answer.isBlank()) throw new BusinessException("AI_RESPONSE_REJECTED", "AI provider returned an empty answer");
      return answer;
    } catch (BusinessException exception) {
      throw exception;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new BusinessException("AI_PROVIDER_UNAVAILABLE", "AI provider request was interrupted");
    } catch (Exception exception) {
      throw new BusinessException("AI_PROVIDER_UNAVAILABLE", "AI provider is unavailable");
    }
  }
}
