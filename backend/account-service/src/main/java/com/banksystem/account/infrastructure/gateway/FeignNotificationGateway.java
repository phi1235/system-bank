package com.banksystem.account.infrastructure.gateway;
import com.banksystem.account.application.gateway.NotificationGateway;
import com.banksystem.account.infrastructure.feign.NotificationClient;
import com.banksystem.account.infrastructure.feign.NotificationClientDtos.CreateNotificationRequest;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FeignNotificationGateway implements NotificationGateway {

  private final NotificationClient notificationClient;
  private final String notificationApiKey;

  public FeignNotificationGateway(
      Optional<NotificationClient> notificationClient,
      @Value("${bank.internal.notification-api-key}") String notificationApiKey) {
    this.notificationClient = notificationClient.orElse(null);
    this.notificationApiKey = notificationApiKey;
  }

  @Override
  public void sendNotification(UUID userId, String template, String body, String resourceType, String resourceId, String targetUrl) {
    if (notificationClient == null) return;
    try {
      notificationClient.createNotification(
          new CreateNotificationRequest(
              "INAPP",
              userId.toString(),
              template,
              "SENT",
              body,
              userId,
              "CUSTOMER",
              resourceType,
              resourceId,
              targetUrl),
          notificationApiKey);
    } catch (Exception ex) {
      // Best-effort inbox notification
    }
  }
}
