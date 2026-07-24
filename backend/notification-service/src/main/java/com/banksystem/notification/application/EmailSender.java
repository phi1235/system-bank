package com.banksystem.notification.application;

/** Outbound email channel used by notification handlers. */
public interface EmailSender {
  void send(String to, String subject, String body);
}
