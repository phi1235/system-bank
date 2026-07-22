package com.banksystem.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ensures every servlet request has {@code X-Correlation-Id}:
 * <ul>
 *   <li>accepts inbound header (e.g. from API gateway / FE)</li>
 *   <li>generates UUID when missing</li>
 *   <li>puts value in SLF4J MDC key {@value #MDC_KEY} for log patterns</li>
 *   <li>echoes header on the response</li>
 * </ul>
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class CorrelationIdFilter extends OncePerRequestFilter {

  /** MDC key used in logging.pattern.level across services. */
  public static final String MDC_KEY = "correlationId";

  /** Request attribute mirror of the resolved correlation id. */
  public static final String REQUEST_ATTR = "bank.correlationId";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String correlationId = resolve(request);
    request.setAttribute(REQUEST_ATTR, correlationId);
    MDC.put(MDC_KEY, correlationId);
    response.setHeader(SecurityHeaders.CORRELATION_ID, correlationId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_KEY);
    }
  }

  static String resolve(HttpServletRequest request) {
    String inbound = request.getHeader(SecurityHeaders.CORRELATION_ID);
    if (inbound != null) {
      String trimmed = inbound.trim();
      if (!trimmed.isEmpty() && trimmed.length() <= 128) {
        return trimmed;
      }
    }
    return UUID.randomUUID().toString();
  }
}
