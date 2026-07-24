package com.banksystem.notification.application;

/** Outbound SMS channel used by notification handlers. */
public interface SmsSender {
  void send(String to, String body);
}
