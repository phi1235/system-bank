package com.banksystem.notification.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "bank.email.provider", havingValue = "mock", matchIfMissing = true)
public class MockEmailSender implements EmailSender {

  private static final Logger log = LoggerFactory.getLogger(MockEmailSender.class);

  @Override
  public void send(String to, String subject, String body) {
    log.info("MOCK_EMAIL to={} subject={} body={}", to, subject, body == null ? "" : body.replace('\n', ' '));
  }
}
