package com.banksystem.notification.config;

import com.banksystem.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

public final class UserContext {
  private UserContext() {}

  public static GatewayUser requireUser() {
    var attrs = RequestContextHolder.getRequestAttributes();
    if (attrs == null) {
      throw new BusinessException("UNAUTHORIZED", "Missing user context", HttpStatus.UNAUTHORIZED);
    }
    Object u = attrs.getAttribute(RequestAuthFilter.ATTR, RequestAttributes.SCOPE_REQUEST);
    if (!(u instanceof GatewayUser gu)) {
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
}
