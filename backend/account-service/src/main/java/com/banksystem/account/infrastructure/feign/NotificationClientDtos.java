package com.banksystem.account.infrastructure.feign;
import com.banksystem.account.application.account.*;
import com.banksystem.account.application.card.*;
import com.banksystem.account.application.deposit.*;
import com.banksystem.account.application.ledger.*;
import com.banksystem.account.domain.account.*;
import com.banksystem.account.domain.card.*;
import com.banksystem.account.domain.deposit.*;
import com.banksystem.account.domain.ledger.*;
import com.banksystem.account.api.dto.*;

public final class NotificationClientDtos {
  private NotificationClientDtos() {}

  public record CreateOpsAlertRequest(String eventId, String template, String body) {}

  /** Customer inbox notification (e.g. card approval decision). */
  public record CreateNotificationRequest(
      String channel,
      String recipient,
      String template,
      String status,
      String body,
      java.util.UUID userId,
      String audience,
      String actionType,
      String actionId,
      String actionPath) {}

  public record NotificationItem(
      String id,
      String channel,
      String template,
      String status,
      String body,
      boolean read
  ) {}
}
