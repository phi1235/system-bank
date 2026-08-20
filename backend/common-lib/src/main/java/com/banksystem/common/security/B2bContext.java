package com.banksystem.common.security;

import com.banksystem.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class B2bContext {

  public static final String ATTR = "b2bPrincipal";

  private B2bContext() {}

  public static B2bClientPrincipal requireClient() {
    Object raw = currentB2bPrincipal();
    if (!(raw instanceof B2bClientPrincipal principal)) {
      throw new BusinessException("UNAUTHORIZED", "Missing B2B client context", HttpStatus.UNAUTHORIZED);
    }
    return principal;
  }

  public static void requireScope(String scope) {
    B2bClientPrincipal client = requireClient();
    if (!client.hasScope(scope)) {
      throw new BusinessException("FORBIDDEN", "Missing required B2B scope: " + scope, HttpStatus.FORBIDDEN);
    }
  }

  public static void requireAnyScope(String... scopes) {
    B2bClientPrincipal client = requireClient();
    if (!client.hasAnyScope(scopes)) {
      throw new BusinessException("FORBIDDEN", "Missing any required B2B scope", HttpStatus.FORBIDDEN);
    }
  }

  public static B2bClientPrincipal getClientOrNull() {
    Object raw = currentB2bPrincipal();
    if (raw instanceof B2bClientPrincipal principal) {
      return principal;
    }
    return null;
  }

  private static Object currentB2bPrincipal() {
    RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
    if (attrs != null) {
      Object fromAttrs = attrs.getAttribute(ATTR, RequestAttributes.SCOPE_REQUEST);
      if (fromAttrs != null) {
        return fromAttrs;
      }
      if (attrs instanceof ServletRequestAttributes sra) {
        HttpServletRequest request = sra.getRequest();
        if (request != null) {
          return request.getAttribute(ATTR);
        }
      }
    }
    return null;
  }
}
