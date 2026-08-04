package com.banksystem.account.application.gateway;
import com.banksystem.account.application.account.*;
import com.banksystem.account.application.card.*;
import com.banksystem.account.application.deposit.*;
import com.banksystem.account.application.ledger.*;
import com.banksystem.account.domain.account.*;
import com.banksystem.account.domain.card.*;
import com.banksystem.account.domain.deposit.*;
import com.banksystem.account.domain.ledger.*;
import com.banksystem.account.api.dto.*;

import java.util.UUID;

public interface NotificationGateway {
  void sendNotification(UUID userId, String template, String body, String resourceType, String resourceId, String targetUrl);
}
