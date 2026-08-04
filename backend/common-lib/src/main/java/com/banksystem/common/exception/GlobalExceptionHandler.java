package com.banksystem.common.exception;

import com.banksystem.common.api.ApiError;
import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.CorrelationIdFilter;
import com.banksystem.common.security.SecurityHeaders;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex, HttpServletRequest request) {
    String correlationId = correlationId(request);
    log.warn("business error code={} message={} path={}", ex.getCode(), ex.getMessage(), request.getRequestURI());
    ApiError error = new ApiError(ex.getCode(), ex.getMessage());
    HttpStatus status = ex.getStatus() != null ? ex.getStatus() : resolveStatus(ex.getCode());
    return ResponseEntity.status(status)
        .body(ApiResponse.fail(error, correlationId));
  }

  private static HttpStatus resolveStatus(String code) {
    if (code == null) return HttpStatus.BAD_REQUEST;
    if (code.endsWith("_NOT_FOUND") || code.contains("NOT_FOUND")) return HttpStatus.NOT_FOUND;
    if (code.equals("FORBIDDEN") || code.equals("ACCESS_DENIED")) return HttpStatus.FORBIDDEN;
    if (code.equals("UNAUTHORIZED")) return HttpStatus.UNAUTHORIZED;
    if (code.equals("CONFLICT") || code.endsWith("_CONFLICT")) return HttpStatus.CONFLICT;
    if (code.contains("INSUFFICIENT") || code.contains("LIMIT") || code.contains("FROZEN") || code.contains("LOCKED")) {
      return HttpStatus.UNPROCESSABLE_ENTITY;
    }
    return HttpStatus.BAD_REQUEST;
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    String correlationId = correlationId(request);
    List<String> details = ex.getBindingResult().getFieldErrors().stream()
        .map(this::formatFieldError)
        .collect(Collectors.toList());
    log.warn("validation error path={} details={}", request.getRequestURI(), details);
    ApiError error = new ApiError("VALIDATION_ERROR", "Request validation failed", details);
    return ResponseEntity.badRequest().body(ApiResponse.fail(error, correlationId));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex, HttpServletRequest request) {
    String correlationId = correlationId(request);
    log.error("unexpected error path={}", request.getRequestURI(), ex);
    ApiError error = new ApiError("INTERNAL_ERROR", "Unexpected server error");
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.fail(error, correlationId));
  }

  private static String correlationId(HttpServletRequest request) {
    String fromMdc = MDC.get(CorrelationIdFilter.MDC_KEY);
    if (fromMdc != null && !fromMdc.isBlank()) {
      return fromMdc;
    }
    Object attr = request.getAttribute(CorrelationIdFilter.REQUEST_ATTR);
    if (attr instanceof String s && !s.isBlank()) {
      return s;
    }
    String header = request.getHeader(SecurityHeaders.CORRELATION_ID);
    return header == null || header.isBlank() ? null : header.trim();
  }

  private String formatFieldError(FieldError fe) {
    return fe.getField() + ": " + fe.getDefaultMessage();
  }
}
