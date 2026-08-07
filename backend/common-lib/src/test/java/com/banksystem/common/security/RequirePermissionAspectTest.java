package com.banksystem.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.banksystem.common.exception.BusinessException;
import java.util.List;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.mock.web.MockHttpServletRequest;

class RequirePermissionAspectTest {

  private final RequirePermissionAspect aspect = new RequirePermissionAspect();

  @AfterEach
  void clear() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void methodAnnotationEnforced() throws Throwable {
    bindUser(new GatewayUser(
        UUID.fromString("11111111-1111-1111-1111-111111111111"),
        List.of("CUSTOMER"),
        List.of("accounts:lookup:view")));
    SampleController target = new SampleController();
    ProceedingJoinPoint pjp = joinPoint(target, "staffList");
    Object result = aspect.enforce(pjp);
    assertEquals("ok", result);
  }

  @Test
  void missingPermissionThrowsForbidden() throws Throwable {
    bindUser(new GatewayUser(
        UUID.fromString("11111111-1111-1111-1111-111111111111"),
        List.of("CUSTOMER"),
        List.of("ib:home:view")));
    SampleController target = new SampleController();
    ProceedingJoinPoint pjp = joinPoint(target, "staffList");
    BusinessException ex = assertThrows(BusinessException.class, () -> aspect.enforce(pjp));
    assertEquals("FORBIDDEN", ex.getCode());
  }

  @Test
  void classLevelAnnotationUsedWhenMethodHasNone() throws Throwable {
    bindUser(new GatewayUser(
        UUID.fromString("11111111-1111-1111-1111-111111111111"),
        List.of("CUSTOMER"),
        List.of("ib:notifications:view")));
    ClassLevelController target = new ClassLevelController();
    ProceedingJoinPoint pjp = joinPoint(target, "inbox");
    assertEquals("inbox", aspect.enforce(pjp));
  }

  private static void bindUser(GatewayUser user) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    ServletRequestAttributes attrs = new ServletRequestAttributes(request);
    attrs.setAttribute(RequestAuthFilter.ATTR, user, RequestAttributes.SCOPE_REQUEST);
    RequestContextHolder.setRequestAttributes(attrs);
  }

  private static ProceedingJoinPoint joinPoint(Object target, String methodName) throws Exception {
    MethodSignature signature = Mockito.mock(MethodSignature.class);
    var method = target.getClass().getMethod(methodName);
    Object invoked = method.invoke(target);
    Mockito.when(signature.getMethod()).thenReturn(method);
    ProceedingJoinPoint pjp = Mockito.mock(ProceedingJoinPoint.class);
    Mockito.when(pjp.getSignature()).thenReturn(signature);
    try {
      Mockito.when(pjp.proceed()).thenReturn(invoked);
    } catch (Throwable t) {
      throw new IllegalStateException(t);
    }
    return pjp;
  }

  static class SampleController {
    @RequirePermission("accounts:lookup:view")
    public String staffList() {
      return "ok";
    }
  }

  @RequirePermission("ib:notifications:view")
  static class ClassLevelController {
    public String inbox() {
      return "inbox";
    }
  }
}
