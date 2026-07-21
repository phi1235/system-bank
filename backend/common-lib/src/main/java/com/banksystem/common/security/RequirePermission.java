package com.banksystem.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declarative permission gate for controller methods (or the whole class).
 * Enforced by {@link RequirePermissionAspect} via {@link UserContext#requirePermission(String)}.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {
  /** Required permission code, e.g. {@code accounts:lookup:view}. */
  String value();
}
