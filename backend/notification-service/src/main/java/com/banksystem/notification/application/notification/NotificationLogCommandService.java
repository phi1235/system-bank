package com.banksystem.notification.application.notification;

import com.banksystem.common.api.PageResponse;
import com.banksystem.notification.api.dto.NotificationDtos.NotificationItem;
import com.banksystem.notification.api.notification.NotificationSandboxController.NotificationSandboxItem;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface NotificationLogCommandService {
  record CreateNotificationLogCommand(
      String channel,
      String recipient,
      String template,
      String status,
      String body,
      UUID userId,
      String audience,
      String actionType,
      String actionId,
      String actionPath
  ) {}

  void verifyInternalKey(String key);
  List<Map<String, Object>> listLogs(UUID eventId);
  PageResponse<NotificationSandboxItem> searchSandbox(
      String q, String channel, Integer page, Integer size
  );
  NotificationItem createNotificationLog(CreateNotificationLogCommand cmd);
}
