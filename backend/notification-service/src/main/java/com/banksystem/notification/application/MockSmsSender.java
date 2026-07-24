package com.banksystem.notification.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "bank.sms.provider", havingValue = "mock", matchIfMissing = true)
public class MockSmsSender implements SmsSender {

  private static final Logger log = LoggerFactory.getLogger(MockSmsSender.class);

  @Override
  public void send(String to, String body) {
    log.info("MOCK_SMS to={} body={}", to, body == null ? "" : body.replace('\n', ' '));
  }
}
