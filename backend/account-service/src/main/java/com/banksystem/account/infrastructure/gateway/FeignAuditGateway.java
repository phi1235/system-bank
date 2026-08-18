package com.banksystem.account.infrastructure.gateway;
import com.banksystem.account.application.gateway.AuditGateway;
import com.banksystem.account.infrastructure.feign.AuditClient;
import com.banksystem.account.infrastructure.feign.AuditClient.CreateAuditLogRequest;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FeignAuditGateway implements AuditGateway {

  private final AuditClient auditClient;
  private final String internalApiKey;

  public FeignAuditGateway(
      Optional<AuditClient> auditClient,
      @Value("${bank.internal.transaction-api-key}") String internalApiKey) {
    this.auditClient = auditClient.orElse(null);
    this.internalApiKey = internalApiKey;
  }

  @Override
  public void recordAuditLog(UUID actorId, String action, String resourceType, String resourceId, String metadata) {
    if (auditClient == null) return;
    CompletableFuture.runAsync(() -> {
      try {
        auditClient.createAuditLog(
            new CreateAuditLogRequest(actorId, action, resourceType, resourceId, "127.0.0.1", metadata),
            internalApiKey);
      } catch (Exception ex) {
        // Best-effort audit logging
      }
    });
  }
}
