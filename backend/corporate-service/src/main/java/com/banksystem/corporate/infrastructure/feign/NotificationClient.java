package com.banksystem.corporate.infrastructure.feign;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.SecurityHeaders;
import com.banksystem.corporate.infrastructure.feign.FeignClientDtos.CreateNotificationLogRequest;
import com.banksystem.corporate.infrastructure.feign.FeignClientDtos.SendEmailRequest;
import com.banksystem.corporate.infrastructure.feign.FeignClientDtos.QueueEmailRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "NOTIFICATION-SERVICE", url = "${bank.feign.notification-url:}")
public interface NotificationClient {

  @PostMapping("/internal/notifications")
  ApiResponse<Object> send(
      @RequestHeader(SecurityHeaders.INTERNAL_API_KEY) String apiKey,
      @RequestBody CreateNotificationLogRequest req);

  @PostMapping("/internal/notifications/email")
  ApiResponse<Boolean> sendEmail(
      @RequestHeader(SecurityHeaders.INTERNAL_API_KEY) String apiKey,
      @RequestBody SendEmailRequest req);

  @PostMapping("/internal/notifications/email/queue")
  ApiResponse<Boolean> queueEmail(
      @RequestHeader(SecurityHeaders.INTERNAL_API_KEY) String apiKey,
      @RequestBody QueueEmailRequest req);
}
