package com.banksystem.notification.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MockEmailSender {

  private static final Logger log = LoggerFactory.getLogger(MockEmailSender.class);

  public void send(String to, String subject, String body) {
    log.info("MOCK_EMAIL to={} subject={} body={}", to, subject, body.replace('\n', ' '));
  }
}
