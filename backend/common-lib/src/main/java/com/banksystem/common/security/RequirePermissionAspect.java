package com.banksystem.common.security;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

/** Declarative authorization for user-facing controller methods and classes. */
@Aspect
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class RequirePermissionAspect {

  @Around("@annotation(required)")
  public Object enforceMethod(
      ProceedingJoinPoint joinPoint, RequirePermission required) throws Throwable {
    UserContext.requirePermission(required.value());
    return joinPoint.proceed();
  }

  @Around("@within(required)")
  public Object enforceClass(
      ProceedingJoinPoint joinPoint, RequirePermission required) throws Throwable {
    if (!hasMethodAnnotation(joinPoint, RequirePermission.class)) {
      UserContext.requirePermission(required.value());
    }
    return joinPoint.proceed();
  }

  @Around("@annotation(required)")
  public Object enforceAnyMethod(
      ProceedingJoinPoint joinPoint, RequireAnyPermission required) throws Throwable {
    UserContext.requireAnyPermission(required.value());
    return joinPoint.proceed();
  }

  @Around("@within(required)")
  public Object enforceAnyClass(
      ProceedingJoinPoint joinPoint, RequireAnyPermission required) throws Throwable {
    if (!hasMethodAnnotation(joinPoint, RequireAnyPermission.class)) {
      UserContext.requireAnyPermission(required.value());
    }
    return joinPoint.proceed();
  }

  public Object enforce(ProceedingJoinPoint joinPoint) throws Throwable {
    Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
    RequirePermission required = AnnotationUtils.findAnnotation(method, RequirePermission.class);
    if (required != null) {
      return enforceMethod(joinPoint, required);
    }
    RequirePermission classReq = AnnotationUtils.findAnnotation(method.getDeclaringClass(), RequirePermission.class);
    if (classReq != null) {
      return enforceClass(joinPoint, classReq);
    }
    return joinPoint.proceed();
  }

  private static boolean hasMethodAnnotation(
      ProceedingJoinPoint joinPoint, Class<? extends Annotation> type) {
    Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
    return AnnotationUtils.findAnnotation(method, type) != null;
  }
}
