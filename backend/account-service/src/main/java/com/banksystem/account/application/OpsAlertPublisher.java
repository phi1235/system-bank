package com.banksystem.account.application;

import com.banksystem.account.domain.AccountEntity;
import com.banksystem.account.infrastructure.feign.NotificationClient;
import com.banksystem.account.infrastructure.feign.NotificationClientDtos.CreateOpsAlertRequest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/** Best-effort OPS alerts; never blocks freeze/unfreeze. */
@Service
public class OpsAlertPublisher {

  private static final Logger log = LoggerFactory.getLogger(OpsAlertPublisher.class);

  public static final String TEMPLATE_ACCOUNT_FROZEN = "OPS_ACCOUNT_FROZEN";
  public static final String TEMPLATE_ACCOUNT_UNFROZEN = "OPS_ACCOUNT_UNFROZEN";

  private final NotificationClient notificationClient;
  private final String apiKey;

  public OpsAlertPublisher(
      @Lazy NotificationClient notificationClient,
      @Value("${bank.internal.notification-api-key}") String apiKey) {
    this.notificationClient = notificationClient;
    this.apiKey = apiKey;
  }

  public void accountFrozen(AccountEntity account) {
    publishAccountStatus(account, TEMPLATE_ACCOUNT_FROZEN, "Account frozen");
  }

  public void accountUnfrozen(AccountEntity account) {
    publishAccountStatus(account, TEMPLATE_ACCOUNT_UNFROZEN, "Account unfrozen");
  }

  private void publishAccountStatus(AccountEntity account, String template, String action) {
    // Include updatedAt so repeated freeze cycles still surface as separate alerts.
    UUID eventId = UUID.nameUUIDFromBytes(
        (template + ":" + account.getId() + ":" + account.getUpdatedAt())
            .getBytes(StandardCharsets.UTF_8));
    String body = action
        + " accountId=" + account.getId()
        + " accountNumber=" + account.getAccountNumber()
        + " userId=" + account.getUserId()
        + " status=" + account.getStatus();
    publishQuietly(eventId, template, body);
  }

  private void publishQuietly(UUID eventId, String template, String body) {
    try {
      notificationClient.createOpsAlert(
          new CreateOpsAlertRequest(eventId.toString(), template, body),
          apiKey);
    } catch (Exception ex) {
      log.warn("Failed to publish OPS alert template={}: {}", template, ex.getMessage());
    }
  }
}
