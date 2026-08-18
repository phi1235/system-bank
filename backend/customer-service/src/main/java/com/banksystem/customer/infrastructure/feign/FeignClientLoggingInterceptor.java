package com.banksystem.customer.infrastructure.feign;

import com.banksystem.common.security.CorrelationIdFilter;
import com.banksystem.common.security.SecurityHeaders;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Propagates correlation ID, user ID and logs inter-service Feign calls.
 */
@Component
public class FeignClientLoggingInterceptor implements RequestInterceptor {

  private static final Logger log = LoggerFactory.getLogger(FeignClientLoggingInterceptor.class);

  @Override
  public void apply(RequestTemplate template) {
    String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
    if (correlationId != null && !correlationId.isBlank()) {
      template.header(SecurityHeaders.CORRELATION_ID, correlationId);
    } else {
      correlationId = "NO-CORR";
    }

    String userId = MDC.get("userId");
    if (userId != null && !userId.isBlank()) {
      template.header(SecurityHeaders.USER_ID, userId);
    }

    String targetName = template.feignTarget() != null ? template.feignTarget().name() : "REMOTE";
    log.info("[FEIGN-REQ] target={} | method={} | uri={}", targetName, template.method(), template.url());
  }
}
