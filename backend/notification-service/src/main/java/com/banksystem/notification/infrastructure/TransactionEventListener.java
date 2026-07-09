package com.banksystem.notification.infrastructure;

import com.banksystem.notification.application.NotificationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TransactionEventListener {

  private static final Logger log = LoggerFactory.getLogger(TransactionEventListener.class);

  private final NotificationHandler handler;

  public TransactionEventListener(NotificationHandler handler) {
    this.handler = handler;
  }

  @KafkaListener(topics = "${bank.kafka.topic-completed}", groupId = "notification-service")
  public void onCompleted(String payload) {
    log.debug("Received completed event: {}", payload);
    handler.handle(payload);
  }

  @KafkaListener(topics = "${bank.kafka.topic-failed}", groupId = "notification-service")
  public void onFailed(String payload) {
    log.debug("Received failed event: {}", payload);
    handler.handle(payload);
  }
}
