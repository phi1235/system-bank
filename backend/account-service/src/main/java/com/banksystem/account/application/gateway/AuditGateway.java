package com.banksystem.account.application.gateway;

import java.util.UUID;

public interface AuditGateway {
  void recordAuditLog(UUID actorId, String action, String resourceType, String resourceId, String metadata);
}
