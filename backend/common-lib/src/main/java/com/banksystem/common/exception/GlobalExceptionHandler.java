package com.banksystem.common.exception;

import com.banksystem.common.api.ApiError;
import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.SecurityHeaders;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex, HttpServletRequest request) {
    String correlationId = request.getHeader(SecurityHeaders.CORRELATION_ID);
    ApiError error = new ApiError(ex.getCode(), ex.getMessage());
    return ResponseEntity.status(ex.getStatus())
        .body(ApiResponse.fail(error, correlationId));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    String correlationId = request.getHeader(SecurityHeaders.CORRELATION_ID);
    List<String> details = ex.getBindingResult().getFieldErrors().stream()
        .map(this::formatFieldError)
        .collect(Collectors.toList());
    ApiError error = new ApiError("VALIDATION_ERROR", "Request validation failed", details);
    return ResponseEntity.badRequest().body(ApiResponse.fail(error, correlationId));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex, HttpServletRequest request) {
    String correlationId = request.getHeader(SecurityHeaders.CORRELATION_ID);
    ApiError error = new ApiError("INTERNAL_ERROR", "Unexpected server error");
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.fail(error, correlationId));
  }

  private String formatFieldError(FieldError fe) {
    return fe.getField() + ": " + fe.getDefaultMessage();
  }
}
