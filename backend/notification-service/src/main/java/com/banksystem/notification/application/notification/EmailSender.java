package com.banksystem.notification.application.notification;

/** Outbound email channel used by notification handlers. */
public interface EmailSender {
  void send(String to, String subject, String body);

  default void sendWithAttachment(String to, String subject, String body, String filename, byte[] content) {
    send(to, subject, body);
  }
}
