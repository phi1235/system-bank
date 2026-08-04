package com.banksystem.notification.application.notification;
import com.banksystem.notification.application.notification.*;
import com.banksystem.notification.domain.notification.*;
import com.banksystem.notification.domain.event.*;
import com.banksystem.notification.api.dto.*;

/** Outbound SMS channel used by notification handlers. */
public interface SmsSender {
  void send(String to, String body);
}
