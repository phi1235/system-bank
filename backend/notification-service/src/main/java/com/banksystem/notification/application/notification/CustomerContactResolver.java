package com.banksystem.notification.application.notification;

import java.util.UUID;

public interface CustomerContactResolver {
  CustomerContact find(UUID userId);

  record CustomerContact(String email, String phone) {
    public static CustomerContact empty() {
      return new CustomerContact(null, null);
    }
  }
}
