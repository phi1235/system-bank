package com.banksystem.common.security;

import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

/**
 * AOP gate for {@link RequirePermission}. Method annotation wins over class-level.
 */
@Aspect
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class RequirePermissionAspect {

  @Around(
      "@within(com.banksystem.common.security.RequirePermission) || "
          + "@annotation(com.banksystem.common.security.RequirePermission)")
  public Object enforce(ProceedingJoinPoint pjp) throws Throwable {
    MethodSignature signature = (MethodSignature) pjp.getSignature();
    Method method = signature.getMethod();
    RequirePermission methodAnn = AnnotationUtils.findAnnotation(method, RequirePermission.class);
    RequirePermission classAnn =
        AnnotationUtils.findAnnotation(method.getDeclaringClass(), RequirePermission.class);
    RequirePermission required = methodAnn != null ? methodAnn : classAnn;
    if (required != null) {
      UserContext.requirePermission(required.value());
    }
    return pjp.proceed();
  }

  @Around(
      "@within(com.banksystem.common.security.RequireAnyPermission) || "
          + "@annotation(com.banksystem.common.security.RequireAnyPermission)")
  public Object enforceAny(ProceedingJoinPoint pjp) throws Throwable {
    MethodSignature signature = (MethodSignature) pjp.getSignature();
    Method method = signature.getMethod();
    RequireAnyPermission methodAnn =
        AnnotationUtils.findAnnotation(method, RequireAnyPermission.class);
    RequireAnyPermission classAnn =
        AnnotationUtils.findAnnotation(method.getDeclaringClass(), RequireAnyPermission.class);
    RequireAnyPermission required = methodAnn != null ? methodAnn : classAnn;
    if (required != null) {
      UserContext.requireAnyPermission(required.value());
    }
    return pjp.proceed();
  }
}
