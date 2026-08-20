package com.banksystem.auth.domain.b2b;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "b2b_client_applications")
public class B2bClientApplicationEntity {

  @Id
  private UUID id;

  @Column(name = "client_id", nullable = false, unique = true, length = 64)
  private String clientId;

  @Column(name = "client_name", nullable = false, length = 255)
  private String clientName;

  @Column(name = "organization_tax_code", nullable = false, length = 32)
  private String organizationTaxCode;

  @Column(nullable = false, length = 20)
  private String status = "ACTIVE";

  @Column(name = "allowed_grant_types", nullable = false, length = 128)
  private String allowedGrantTypes = "client_credentials";

  @Column(name = "allowed_scopes", nullable = false, length = 512)
  private String allowedScopes;

  @Column(name = "token_endpoint_auth_method", nullable = false, length = 32)
  private String tokenEndpointAuthMethod = "private_key_jwt";

  @Column(name = "jwks_uri", length = 1024)
  private String jwksUri;

  @Column(name = "public_key_pem", columnDefinition = "TEXT")
  private String publicKeyPem;

  @Column(name = "client_cert_thumbprint_sha256", length = 128)
  private String clientCertThumbprintSha256;

  @Column(name = "webhook_callback_url", length = 1024)
  private String webhookCallbackUrl;

  @Column(name = "webhook_secret", length = 256)
  private String webhookSecret;

  @Column(name = "rate_limit_rpm", nullable = false)
  private int rateLimitRpm = 120;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public static B2bClientApplicationEntity create(
      UUID id,
      String clientId,
      String clientName,
      String organizationTaxCode,
      String allowedScopes,
      String publicKeyPem,
      String clientCertThumbprintSha256,
      String webhookCallbackUrl,
      String webhookSecret,
      int rateLimitRpm,
      Instant now) {
    B2bClientApplicationEntity entity = new B2bClientApplicationEntity();
    entity.id = id != null ? id : UUID.randomUUID();
    entity.clientId = clientId.trim();
    entity.clientName = clientName.trim();
    entity.organizationTaxCode = organizationTaxCode.trim();
    entity.status = "ACTIVE";
    entity.allowedGrantTypes = "client_credentials";
    entity.allowedScopes = allowedScopes != null ? allowedScopes.trim() : "openbanking:accounts:read openbanking:statements:read openbanking:payments:write openbanking:payments:bulk:write openbanking:payments:read";
    entity.tokenEndpointAuthMethod = "private_key_jwt";
    entity.publicKeyPem = publicKeyPem != null ? publicKeyPem.trim() : null;
    entity.clientCertThumbprintSha256 = clientCertThumbprintSha256 != null ? clientCertThumbprintSha256.trim() : null;
    entity.webhookCallbackUrl = webhookCallbackUrl != null ? webhookCallbackUrl.trim() : null;
    entity.webhookSecret = webhookSecret != null ? webhookSecret.trim() : null;
    entity.rateLimitRpm = rateLimitRpm > 0 ? rateLimitRpm : 120;
    entity.createdAt = now;
    entity.updatedAt = now;
    return entity;
  }

  public List<String> scopeList() {
    if (allowedScopes == null || allowedScopes.isBlank()) {
      return List.of();
    }
    return Arrays.stream(allowedScopes.split("[\\s,]+"))
        .filter(s -> !s.isBlank())
        .toList();
  }

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getClientId() { return clientId; }
  public void setClientId(String clientId) { this.clientId = clientId; }
  public String getClientName() { return clientName; }
  public void setClientName(String clientName) { this.clientName = clientName; }
  public String getOrganizationTaxCode() { return organizationTaxCode; }
  public void setOrganizationTaxCode(String organizationTaxCode) { this.organizationTaxCode = organizationTaxCode; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getAllowedGrantTypes() { return allowedGrantTypes; }
  public void setAllowedGrantTypes(String allowedGrantTypes) { this.allowedGrantTypes = allowedGrantTypes; }
  public String getAllowedScopes() { return allowedScopes; }
  public void setAllowedScopes(String allowedScopes) { this.allowedScopes = allowedScopes; }
  public String getTokenEndpointAuthMethod() { return tokenEndpointAuthMethod; }
  public void setTokenEndpointAuthMethod(String tokenEndpointAuthMethod) { this.tokenEndpointAuthMethod = tokenEndpointAuthMethod; }
  public String getJwksUri() { return jwksUri; }
  public void setJwksUri(String jwksUri) { this.jwksUri = jwksUri; }
  public String getPublicKeyPem() { return publicKeyPem; }
  public void setPublicKeyPem(String publicKeyPem) { this.publicKeyPem = publicKeyPem; }
  public String getClientCertThumbprintSha256() { return clientCertThumbprintSha256; }
  public void setClientCertThumbprintSha256(String clientCertThumbprintSha256) { this.clientCertThumbprintSha256 = clientCertThumbprintSha256; }
  public String getWebhookCallbackUrl() { return webhookCallbackUrl; }
  public void setWebhookCallbackUrl(String webhookCallbackUrl) { this.webhookCallbackUrl = webhookCallbackUrl; }
  public String getWebhookSecret() { return webhookSecret; }
  public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }
  public int getRateLimitRpm() { return rateLimitRpm; }
  public void setRateLimitRpm(int rateLimitRpm) { this.rateLimitRpm = rateLimitRpm; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
