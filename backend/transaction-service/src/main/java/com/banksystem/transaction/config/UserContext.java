package com.banksystem.transaction.config;

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

  public static void requireAdmin() {
    if (!requireUser().hasRole("ADMIN")) {
      throw new BusinessException("FORBIDDEN", "Admin role required", HttpStatus.FORBIDDEN);
    }
  }
}
