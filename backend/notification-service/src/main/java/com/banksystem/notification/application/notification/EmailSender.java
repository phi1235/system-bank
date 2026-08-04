package com.banksystem.notification.application.notification;
import com.banksystem.notification.application.notification.*;
import com.banksystem.notification.domain.notification.*;
import com.banksystem.notification.domain.event.*;
import com.banksystem.notification.api.dto.*;

/** Outbound email channel used by notification handlers. */
public interface EmailSender {
  void send(String to, String subject, String body);
}
