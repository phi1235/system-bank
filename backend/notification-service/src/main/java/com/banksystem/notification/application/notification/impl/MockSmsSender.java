package com.banksystem.notification.application.notification.impl;
import com.banksystem.notification.application.notification.SmsSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "bank.sms.provider", havingValue = "mock")
public class MockSmsSender implements SmsSender {

  private static final Logger log = LoggerFactory.getLogger(MockSmsSender.class);

  @Override
  public void send(String to, String body) {
    log.info("Mock SMS delivery accepted");
  }
}
