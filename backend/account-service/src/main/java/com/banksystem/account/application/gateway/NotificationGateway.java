package com.banksystem.account.application.gateway;

import java.util.UUID;

public interface NotificationGateway {
  void sendNotification(UUID userId, String template, String body, String resourceType, String resourceId, String targetUrl);
}
