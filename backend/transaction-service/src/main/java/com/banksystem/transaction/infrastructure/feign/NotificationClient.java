package com.banksystem.transaction.infrastructure.feign;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.transaction.infrastructure.feign.NotificationClientDtos.CreateOpsAlertRequest;
import com.banksystem.transaction.infrastructure.feign.NotificationClientDtos.NotificationItem;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "NOTIFICATION-SERVICE", url = "${NOTIFICATION_SERVICE_URL:}")
public interface NotificationClient {

  @PostMapping("/internal/notifications/ops-alerts")
  ApiResponse<NotificationItem> createOpsAlert(
      @RequestBody CreateOpsAlertRequest request,
      @RequestHeader("X-Internal-Api-Key") String apiKey);
}
