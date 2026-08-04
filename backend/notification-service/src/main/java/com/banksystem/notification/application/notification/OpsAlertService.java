package com.banksystem.notification.application.notification;

import com.banksystem.notification.api.dto.OpsAlertDtos.CreateOpsAlertRequest;
import com.banksystem.notification.api.dto.NotificationDtos.NotificationItem;

public interface OpsAlertService {
  NotificationItem create(CreateOpsAlertRequest req);
}
