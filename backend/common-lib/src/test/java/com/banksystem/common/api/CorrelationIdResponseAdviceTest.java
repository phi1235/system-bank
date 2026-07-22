package com.banksystem.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.banksystem.common.security.CorrelationIdFilter;
import com.banksystem.common.security.SecurityHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdResponseAdviceTest {

  private final CorrelationIdResponseAdvice advice = new CorrelationIdResponseAdvice();

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void fillsMissingCorrelationFromMdc() throws Exception {
    MDC.put(CorrelationIdFilter.MDC_KEY, "mdc-corr");
    MockHttpServletRequest servletRequest = new MockHttpServletRequest();
    MockHttpServletResponse servletResponse = new MockHttpServletResponse();
    ApiResponse<String> body = ApiResponse.ok("ok");

    Object out = advice.beforeBodyWrite(
        body,
        dummyParam(),
        MediaType.APPLICATION_JSON,
        MappingJackson2HttpMessageConverter.class,
        new ServletServerHttpRequest(servletRequest),
        new ServletServerHttpResponse(servletResponse));

    @SuppressWarnings("unchecked")
    ApiResponse<String> typed = (ApiResponse<String>) out;
    assertNotNull(typed.meta());
    assertEquals("mdc-corr", typed.meta().correlationId());
    assertEquals("mdc-corr", servletResponse.getHeader(SecurityHeaders.CORRELATION_ID));
  }

  @Test
  void leavesExistingCorrelationUntouched() throws Exception {
    MDC.put(CorrelationIdFilter.MDC_KEY, "mdc-corr");
    MockHttpServletRequest servletRequest = new MockHttpServletRequest();
    MockHttpServletResponse servletResponse = new MockHttpServletResponse();
    ApiResponse<String> body = ApiResponse.ok("ok", "already-set");

    Object out = advice.beforeBodyWrite(
        body,
        dummyParam(),
        MediaType.APPLICATION_JSON,
        MappingJackson2HttpMessageConverter.class,
        new ServletServerHttpRequest(servletRequest),
        new ServletServerHttpResponse(servletResponse));

    assertSame(body, out);
  }

  @Test
  void ignoresNonApiResponseBodies() throws Exception {
    Object body = "plain";
    Object out = advice.beforeBodyWrite(
        body,
        dummyParam(),
        MediaType.APPLICATION_JSON,
        MappingJackson2HttpMessageConverter.class,
        new ServletServerHttpRequest(new MockHttpServletRequest()),
        new ServletServerHttpResponse(new MockHttpServletResponse()));
    assertSame(body, out);
  }

  private static MethodParameter dummyParam() throws NoSuchMethodException {
    return new MethodParameter(Object.class.getMethod("toString"), -1);
  }
}
