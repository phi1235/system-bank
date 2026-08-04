package com.banksystem.notification.application.notification;

import com.banksystem.common.api.PageResponse;
import com.banksystem.notification.api.dto.NotificationDtos.NotificationItem;
import java.util.UUID;

public interface NotificationInboxService {
  String AUDIENCE_CUSTOMER = "CUSTOMER";
  String AUDIENCE_OPS = "OPS";

  PageResponse<NotificationItem> myInbox(UUID userId, int page, int size);
  PageResponse<NotificationItem> myInbox(UUID userId, Integer page, Integer size, String readFilter);
  long unreadCount(UUID userId);
  NotificationItem markRead(UUID userId, UUID id);
  int markAllRead(UUID userId);
  PageResponse<NotificationItem> opsInbox(Integer page, Integer size);
  long opsUnreadCount();
  NotificationItem markOpsRead(UUID id);
  int markAllOpsRead();
}
