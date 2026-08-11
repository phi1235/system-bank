package com.banksystem.notification.infrastructure.customer;

import com.banksystem.common.security.SecurityHeaders;
import com.banksystem.notification.application.notification.CustomerContactResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HttpCustomerContactResolver implements CustomerContactResolver {
  private static final Logger log = LoggerFactory.getLogger(HttpCustomerContactResolver.class);

  private final String baseUrl;
  private final String apiKey;
  private final ObjectMapper objectMapper;
  private final HttpClient client;

  public HttpCustomerContactResolver(
      @Value("${bank.customer-service-url}") String baseUrl,
      @Value("${bank.customer-internal-api-key}") String apiKey,
      ObjectMapper objectMapper) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.apiKey = apiKey;
    this.objectMapper = objectMapper;
    this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
  }

  @Override
  public CustomerContact find(UUID userId) {
    try {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(baseUrl + "/internal/customers/" + userId + "/contact"))
          .timeout(Duration.ofSeconds(5))
          .header(SecurityHeaders.INTERNAL_API_KEY, apiKey)
          .GET()
          .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        log.warn("Customer contact lookup failed userId={} status={}", userId, response.statusCode());
        return CustomerContact.empty();
      }
      JsonNode data = objectMapper.readTree(response.body()).path("data");
      return new CustomerContact(text(data, "email"), text(data, "phone"));
    } catch (Exception ex) {
      log.warn("Customer contact lookup unavailable userId={} reason={}", userId, ex.getMessage());
      return CustomerContact.empty();
    }
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isMissingNode() || value.isNull() || value.asText().isBlank()
        ? null : value.asText().trim();
  }
}
