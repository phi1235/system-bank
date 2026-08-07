package com.banksystem.common.api;

import com.banksystem.common.security.CorrelationIdFilter;
import com.banksystem.common.security.SecurityHeaders;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Fills {@link Meta#correlationId()} on {@link ApiResponse} when missing,
 * using MDC / request attribute / inbound header (same order as filters).
 */
@RestControllerAdvice
public class CorrelationIdResponseAdvice implements ResponseBodyAdvice<Object> {

  @Override
  public boolean supports(
      MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
    return true;
  }

  @Override
  public Object beforeBodyWrite(
      Object body,
      MethodParameter returnType,
      MediaType selectedContentType,
      Class<? extends HttpMessageConverter<?>> selectedConverterType,
      ServerHttpRequest request,
      ServerHttpResponse response) {
    if (!(body instanceof ApiResponse<?> api)) {
      return body;
    }
    Meta meta = api.meta();
    if (meta != null && meta.correlationId() != null && !meta.correlationId().isBlank()) {
      return body;
    }
    String correlationId = resolveCorrelationId(request);
    if (correlationId == null || correlationId.isBlank()) {
      return body;
    }
    response.getHeaders().set(SecurityHeaders.CORRELATION_ID, correlationId);
    if (response instanceof ServletServerHttpResponse servletRes) {
      servletRes.getServletResponse().setHeader(SecurityHeaders.CORRELATION_ID, correlationId);
    }
    return new ApiResponse<>(api.success(), api.data(), api.error(), Meta.now(correlationId));
  }

  private static String resolveCorrelationId(ServerHttpRequest request) {
    String fromMdc = MDC.get(CorrelationIdFilter.MDC_KEY);
    if (fromMdc != null && !fromMdc.isBlank()) {
      return fromMdc;
    }
    if (request instanceof ServletServerHttpRequest servletReq) {
      HttpServletRequest http = servletReq.getServletRequest();
      Object attr = http.getAttribute(CorrelationIdFilter.REQUEST_ATTR);
      if (attr instanceof String s && !s.isBlank()) {
        return s;
      }
      String header = http.getHeader(SecurityHeaders.CORRELATION_ID);
      if (header != null && !header.isBlank()) {
        return header.trim();
      }
    }
    String header = request.getHeaders().getFirst(SecurityHeaders.CORRELATION_ID);
    return header == null || header.isBlank() ? null : header.trim();
  }
}
