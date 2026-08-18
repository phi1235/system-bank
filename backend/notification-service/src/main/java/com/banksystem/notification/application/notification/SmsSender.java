package com.banksystem.notification.application.notification;

/** Outbound SMS channel used by notification handlers. */
public interface SmsSender {
  void send(String to, String body);
}
