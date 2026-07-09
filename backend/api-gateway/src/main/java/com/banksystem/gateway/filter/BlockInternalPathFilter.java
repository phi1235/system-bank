package com.banksystem.gateway.filter;

import com.banksystem.common.security.SecurityHeaders;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class BlockInternalPathFilter implements GlobalFilter, Ordered {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String path = exchange.getRequest().getURI().getPath();
    if (path.contains("/internal/")) {
      exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
      exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
      String correlationId = exchange.getRequest().getHeaders().getFirst(SecurityHeaders.CORRELATION_ID);
      byte[] bytes = toJson(correlationId);
      DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
      return exchange.getResponse().writeWith(Mono.just(buffer));
    }
    return chain.filter(exchange);
  }

  private byte[] toJson(String correlationId) {
    try {
      return objectMapper.writeValueAsBytes(Map.of(
          "success", false,
          "error", Map.of(
              "code", "INTERNAL_PATH_FORBIDDEN",
              "message", "Internal APIs are not exposed through the gateway"
          ),
          "meta", Map.of(
              "correlationId", correlationId == null ? "" : correlationId
          )
      ));
    } catch (JsonProcessingException e) {
      return "{\"success\":false}".getBytes(StandardCharsets.UTF_8);
    }
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 1;
  }
}
