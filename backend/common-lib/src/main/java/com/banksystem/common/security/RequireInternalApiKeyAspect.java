package com.banksystem.common.security;

import com.banksystem.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Central security gate for endpoints annotated with {@link RequireInternalApiKey}. */
@Aspect
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class RequireInternalApiKeyAspect {

  private final HttpServletRequest request;
  private final String expectedKey;

  public RequireInternalApiKeyAspect(
      HttpServletRequest request,
      @Value("${bank.internal.api-key}") String expectedKey) {
    this.request = request;
    this.expectedKey = expectedKey;
  }

  @Around("@annotation(required)")
  public Object enforceMethod(
      ProceedingJoinPoint joinPoint, RequireInternalApiKey required) throws Throwable {
    return verifyAndProceed(joinPoint);
  }

  @Around("@within(required)")
  public Object enforceClass(
      ProceedingJoinPoint joinPoint, RequireInternalApiKey required) throws Throwable {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    if (AnnotationUtils.findAnnotation(
        signature.getMethod(), RequireInternalApiKey.class) != null) {
      return joinPoint.proceed();
    }
    return verifyAndProceed(joinPoint);
  }

  private Object verifyAndProceed(ProceedingJoinPoint joinPoint) throws Throwable {
    if (expectedKey.isBlank()) {
      throw new IllegalStateException("bank.internal.api-key must be configured");
    }
    String suppliedKey = request.getHeader(SecurityHeaders.INTERNAL_API_KEY);
    if (!SecretVerifier.matches(suppliedKey, expectedKey)) {
      throw new BusinessException("FORBIDDEN", "Invalid internal API key", HttpStatus.FORBIDDEN);
    }
    return joinPoint.proceed();
  }
}
