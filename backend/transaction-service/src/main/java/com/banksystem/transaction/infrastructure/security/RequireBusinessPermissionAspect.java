package com.banksystem.transaction.infrastructure.security;

import java.time.Instant;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.common.security.GatewayUser;
import com.banksystem.common.security.UserContext;
import com.banksystem.transaction.infrastructure.feign.AuthBusinessClient;
import com.banksystem.transaction.infrastructure.feign.AuthBusinessClient.BusinessMembershipView;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RequireBusinessPermissionAspect {

  private static final Logger log = LoggerFactory.getLogger(RequireBusinessPermissionAspect.class);

  private static final Set<String> SENSITIVE_PERMISSIONS = Set.of(
      "business:credentials:manage",
      "business:settlements:execute",
      "settlement:approve",
      "payout:approve"
  );

  private static final long READ_CACHE_TTL_MS = 30_000L; // 30s TTL for read-only permissions

  private final AuthBusinessClient authBusinessClient;
  private final ExpressionParser expressionParser = new SpelExpressionParser();
  private final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
  private final Map<String, CacheEntry> readMembershipCache = new ConcurrentHashMap<>();

  public RequireBusinessPermissionAspect(AuthBusinessClient authBusinessClient) {
    this.authBusinessClient = authBusinessClient;
  }

  @Around("@annotation(annotation)")
  public Object enforce(ProceedingJoinPoint joinPoint, RequireBusinessPermission annotation) throws Throwable {
    GatewayUser user = UserContext.requireUser();
    String requiredPermission = annotation.value();
    UUID businessId = resolveBusinessId(joinPoint, annotation.businessIdParam());

    if (businessId == null) {
      log.error("[B2B-AUTH] Could not resolve businessId from param {}", annotation.businessIdParam());
      throw new BusinessException("FORBIDDEN", "Missing organization ID in request context");
    }

    boolean isSensitive = SENSITIVE_PERMISSIONS.contains(requiredPermission);
    String cacheKey = businessId + ":" + user.userId() + ":" + requiredPermission;

    if (!isSensitive) {
      CacheEntry entry = readMembershipCache.get(cacheKey);
      if (entry != null && entry.expiresAt() > Instant.now().toEpochMilli()) {
        if (entry.allowed()) {
          return joinPoint.proceed();
        } else {
          throw new BusinessException("FORBIDDEN", "Access denied for business organization " + businessId);
        }
      }
    }

    // Call Auth-Service to verify tenant membership and permission (fail-closed)
    boolean allowed = false;
    try {
      ApiResponse<BusinessMembershipView> resp = authBusinessClient.verifyMembership(
          businessId, user.userId(), requiredPermission
      );
      if (resp != null && resp.data() != null) {
        BusinessMembershipView view = resp.data();
        if (view.valid() && "ACTIVE".equalsIgnoreCase(view.status())) {
          allowed = true;
        }
      }
    } catch (Exception e) {
      log.error("[B2B-AUTH] Failed to verify membership with auth-service: {}", e.getMessage());
      throw new BusinessException("FORBIDDEN", "Authorization verification failed or auth service unavailable");
    }

    if (!allowed) {
      if (!isSensitive) {
        readMembershipCache.put(cacheKey, new CacheEntry(false, Instant.now().toEpochMilli() + READ_CACHE_TTL_MS));
      }
      log.warn("[B2B-AUTH] Denied user={} permission={} on org={}", user.userId(), requiredPermission, businessId);
      throw new BusinessException("FORBIDDEN", "Access denied: insufficient permissions for business organization " + businessId);
    }

    if (!isSensitive) {
      readMembershipCache.put(cacheKey, new CacheEntry(true, Instant.now().toEpochMilli() + READ_CACHE_TTL_MS));
    } else {
      // Audit log for sensitive operations
      log.info("[B2B-AUDIT] SENSITIVE OPERATION: User {} executed {} on Business Org {} at {}",
          user.userId(), requiredPermission, businessId, Instant.now());
    }

    return joinPoint.proceed();
  }

  private UUID resolveBusinessId(ProceedingJoinPoint joinPoint, String paramExpression) {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Method method = signature.getMethod();
    Object[] args = joinPoint.getArgs();

    if (paramExpression.startsWith("#")) {
      // SpEL expression evaluation
      EvaluationContext context = new StandardEvaluationContext();
      String[] paramNames = parameterNameDiscoverer.getParameterNames(method);
      if (paramNames != null) {
        for (int i = 0; i < paramNames.length; i++) {
          context.setVariable(paramNames[i], args[i]);
        }
      }
      Object val = expressionParser.parseExpression(paramExpression).getValue(context);
      return parseUuid(val);
    }

    // Argument by parameter name
    String[] paramNames = parameterNameDiscoverer.getParameterNames(method);
    if (paramNames != null) {
      for (int i = 0; i < paramNames.length; i++) {
        if (paramExpression.equalsIgnoreCase(paramNames[i]) || "organizationId".equalsIgnoreCase(paramNames[i]) || "businessId".equalsIgnoreCase(paramNames[i])) {
          return parseUuid(args[i]);
        }
      }
    }

    // Direct scan for UUID or String in arguments
    for (Object arg : args) {
      if (arg instanceof UUID u) {
        return u;
      }
    }

    return null;
  }

  private UUID parseUuid(Object val) {
    if (val == null) return null;
    if (val instanceof UUID u) return u;
    try {
      return UUID.fromString(val.toString());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private record CacheEntry(boolean allowed, long expiresAt) {}
}
