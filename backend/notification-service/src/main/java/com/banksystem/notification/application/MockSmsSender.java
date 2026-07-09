package com.banksystem.notification.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MockSmsSender {

  private static final Logger log = LoggerFactory.getLogger(MockSmsSender.class);

  public void send(String to, String body) {
    log.info("MOCK_SMS to={} body={}", to, body);
  }
}
