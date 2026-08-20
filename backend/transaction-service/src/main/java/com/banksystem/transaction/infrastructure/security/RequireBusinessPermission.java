package com.banksystem.transaction.infrastructure.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireBusinessPermission {

  /**
   * The required business permission code (e.g. "business:orders:manage", "business:va:view").
   */
  String value();

  /**
   * SpEL expression or method argument name representing the business/organization ID (UUID or String).
   * Defaults to "organizationId".
   */
  String businessIdParam() default "organizationId";
}
