package com.banksystem.account.infrastructure.feign;

import com.banksystem.account.infrastructure.feign.NotificationClientDtos.CreateNotificationRequest;
import com.banksystem.account.infrastructure.feign.NotificationClientDtos.CreateOpsAlertRequest;
import com.banksystem.account.infrastructure.feign.NotificationClientDtos.NotificationItem;
import com.banksystem.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "NOTIFICATION-SERVICE", url = "${bank.feign.notification-url}")
public interface NotificationClient {

  @PostMapping("/internal/notifications/ops-alerts")
  ApiResponse<NotificationItem> createOpsAlert(
      @RequestBody CreateOpsAlertRequest request,
      @RequestHeader("X-Internal-Api-Key") String apiKey);

  /** Customer inbox notification (e.g. card approval decision). */
  @PostMapping("/internal/notifications")
  ApiResponse<NotificationItem> createNotification(
      @RequestBody CreateNotificationRequest request,
      @RequestHeader("X-Internal-Api-Key") String apiKey);
}
