package com.banksystem.common.logging;

import com.banksystem.common.security.CorrelationIdFilter;
import com.banksystem.common.security.SecurityHeaders;
import com.banksystem.common.security.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Enterprise Banking HTTP Request/Response Logging Filter (HDBank KHDN Service Standard).
 * Formats full HTTP audit blocks matching HDBank AuthTokenFilter/processLogData.
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class HttpLoggingFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(HttpLoggingFilter.class);

  public static final String MDC_USER_ID = "userId";
  public static final String MDC_CLIENT_IP = "clientIp";

  private static final Set<String> IGNORED_PATHS = Set.of(
      "/actuator/health",
      "/actuator/prometheus",
      "/actuator/info",
      "/swagger-ui",
      "/v3/api-docs",
      "/favicon.ico"
  );

  private static final Set<String> SENSITIVE_HEADERS = Set.of(
      "authorization", "proxy-authorization", "x-api-key"
  );

  private static final Pattern SENSITIVE_JSON_PATTERN = Pattern.compile(
      "(?i)\"(password|pwd|pin|cvv|secret|token|pan|cardNumber)\"\\s*:\\s*\"([^\"]+)\"");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String uri = request.getRequestURI();
    if (shouldSkipLogging(uri)) {
      filterChain.doFilter(request, response);
      return;
    }

    ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
    ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

    String correlationId = (String) request.getAttribute(CorrelationIdFilter.REQUEST_ATTR);
    if (correlationId == null || correlationId.isBlank()) {
      correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
      if (correlationId == null) {
        correlationId = "";
      }
    }

    String userId = request.getHeader(SecurityHeaders.USER_ID);
    if (userId == null || userId.isBlank()) {
      userId = "";
    }
    MDC.put(MDC_USER_ID, userId);

    String clientIp = UserContext.clientIp(request);
    MDC.put(MDC_CLIENT_IP, clientIp);

    long startMs = System.currentTimeMillis();

    try {
      filterChain.doFilter(wrappedRequest, wrappedResponse);
    } finally {
      long durationMs = System.currentTimeMillis() - startMs;
      processLogData(wrappedRequest, wrappedResponse, clientIp, durationMs);
      wrappedResponse.copyBodyToResponse();
      MDC.remove(MDC_USER_ID);
      MDC.remove(MDC_CLIENT_IP);
    }
  }

  public void processLogData(
      ContentCachingRequestWrapper request,
      ContentCachingResponseWrapper response,
      String clientIp,
      long durationMs) {

    String timestamp = Instant.now().toString();
    String method = request.getMethod();
    String uri = request.getRequestURI();
    String queryString = request.getQueryString();
    String fullUri = (queryString != null && !queryString.isBlank()) ? (uri + "?" + queryString) : uri;
    String protocol = request.getProtocol();
    String userAgent = request.getHeader("User-Agent");
    String referrer = request.getHeader("Referer");
    String sessionId = request.getRequestedSessionId();

    Map<String, String> reqHeaders = extractHeaders(request);
    byte[] reqBuf = request.getContentAsByteArray();
    int reqSize = reqBuf.length;
    String reqBody = extractRequestBody(reqBuf, reqHeaders.get("content-type"));

    int resStatus = response.getStatus();
    Map<String, String> resHeaders = extractResponseHeaders(response);
    byte[] resBuf = response.getContentAsByteArray();
    int resSize = resBuf.length;
    String resBody = "HIDE";

    StringBuilder sb = new StringBuilder(1024);
    sb.append(":========== HTTP LOG ==========\n");
    sb.append("Timestamp       : ").append(timestamp).append("\n");
    sb.append("Client IP       : ").append(clientIp).append("\n");
    sb.append("Method          : ").append(method).append("\n");
    sb.append("Request URI     : ").append(fullUri).append("\n");
    sb.append("Protocol        : ").append(protocol != null ? protocol : "HTTP/1.1").append("\n");
    sb.append("User Agent      : ").append(userAgent != null ? userAgent : "null").append("\n");
    sb.append("Referrer        : ").append(referrer != null ? referrer : "null").append("\n");
    sb.append("Session ID      : ").append(sessionId != null ? sessionId : "null").append("\n");
    sb.append("Request Headers : ").append(toJson(reqHeaders)).append("\n");
    sb.append("Request Size    : ").append(reqSize).append(" bytes\n");
    sb.append("Request Body    : ").append(reqBody).append("\n");
    sb.append("Response Status : ").append(resStatus).append("\n");
    sb.append("Response Headers: ").append(toJson(resHeaders)).append("\n");
    sb.append("Response Size   : ").append(resSize).append(" bytes\n");
    sb.append("Response Body   : ").append(resBody).append("\n");
    sb.append("Response Time   : ").append(durationMs).append(" ms\n");
    sb.append("================================");

    log.info(sb.toString());
  }

  private Map<String, String> extractHeaders(HttpServletRequest request) {
    Map<String, String> map = new LinkedHashMap<>();
    Enumeration<String> names = request.getHeaderNames();
    if (names != null) {
      while (names.hasMoreElements()) {
        String name = names.nextElement();
        String lower = name.toLowerCase();
        if (SENSITIVE_HEADERS.contains(lower)) {
          map.put(lower, "***");
        } else {
          map.put(lower, request.getHeader(name));
        }
      }
    }
    return map;
  }

  private Map<String, String> extractResponseHeaders(HttpServletResponse response) {
    Map<String, String> map = new LinkedHashMap<>();
    for (String name : response.getHeaderNames()) {
      map.put(name, response.getHeader(name));
    }
    return map;
  }

  private String extractRequestBody(byte[] buf, String contentType) {
    if (buf == null || buf.length == 0) {
      return "{}";
    }
    if (contentType != null && (contentType.contains("multipart") || contentType.contains("octet-stream"))) {
      return "HIDE";
    }
    String text = new String(buf, StandardCharsets.UTF_8).trim();
    if (text.isEmpty()) {
      return "{}";
    }
    return SENSITIVE_JSON_PATTERN.matcher(text).replaceAll("\"$1\":\"***\"");
  }

  private String toJson(Map<String, String> map) {
    if (map == null || map.isEmpty()) {
      return "{}";
    }
    StringBuilder sb = new StringBuilder("{");
    boolean first = true;
    for (Map.Entry<String, String> entry : map.entrySet()) {
      if (!first) {
        sb.append(",");
      }
      first = false;
      sb.append("\"").append(entry.getKey()).append("\":");
      if (entry.getValue() == null) {
        sb.append("null");
      } else {
        sb.append("\"").append(entry.getValue().replace("\"", "\\\"")).append("\"");
      }
    }
    sb.append("}");
    return sb.toString();
  }

  private boolean shouldSkipLogging(String uri) {
    if (uri == null) {
      return true;
    }
    for (String prefix : IGNORED_PATHS) {
      if (uri.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }
}
