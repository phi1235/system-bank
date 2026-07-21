package com.banksystem.common.security;

import com.banksystem.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Request-scoped access to the gateway principal set by {@link RequestAuthFilter}.
 */
public final class UserContext {
  private UserContext() {}

  public static GatewayUser requireUser() {
    Object raw = currentGatewayUser();
    if (!(raw instanceof GatewayUser gu)) {
      throw new BusinessException("UNAUTHORIZED", "Missing user context", HttpStatus.UNAUTHORIZED);
    }
    return gu;
  }

  public static void requirePermission(String permission) {
    GatewayUser u = requireUser();
    if (!u.hasPermission(permission)) {
      throw new BusinessException(
          "FORBIDDEN", "Missing permission: " + permission, HttpStatus.FORBIDDEN);
    }
  }

  private static Object currentGatewayUser() {
    RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
    if (attrs != null) {
      Object fromAttrs = attrs.getAttribute(RequestAuthFilter.ATTR, RequestAttributes.SCOPE_REQUEST);
      if (fromAttrs != null) {
        return fromAttrs;
      }
      if (attrs instanceof ServletRequestAttributes sra) {
        HttpServletRequest request = sra.getRequest();
        if (request != null) {
          return request.getAttribute(RequestAuthFilter.ATTR);
        }
      }
    }
    return null;
  }
}
