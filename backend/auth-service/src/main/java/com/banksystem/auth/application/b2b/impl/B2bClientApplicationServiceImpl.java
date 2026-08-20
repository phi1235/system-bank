package com.banksystem.auth.application.b2b.impl;

import com.banksystem.auth.api.dto.B2bDtos.B2bClientCreateRequest;
import com.banksystem.auth.api.dto.B2bDtos.B2bClientResponse;
import com.banksystem.auth.api.dto.B2bDtos.B2bClientUpdateRequest;
import com.banksystem.auth.application.b2b.B2bClientApplicationService;
import com.banksystem.auth.application.b2b.query.B2bClientSearchQuery;
import com.banksystem.auth.domain.b2b.B2bClientApplicationEntity;
import com.banksystem.auth.domain.b2b.B2bClientApplicationRepository;
import com.banksystem.common.exception.BusinessException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class B2bClientApplicationServiceImpl implements B2bClientApplicationService {

  private final B2bClientApplicationRepository repository;

  public B2bClientApplicationServiceImpl(B2bClientApplicationRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional(readOnly = true)
  public Page<B2bClientResponse> listClients(B2bClientSearchQuery query) {
    return repository.searchClients(query.status(), query.q(), query.toPageable())
        .map(this::toResponse);
  }

  @Override
  @Transactional(readOnly = true)
  public B2bClientResponse getClient(String clientId) {
    return repository.findByClientId(clientId)
        .map(this::toResponse)
        .orElseThrow(() -> new BusinessException("CLIENT_NOT_FOUND", "B2B client not found: " + clientId, HttpStatus.NOT_FOUND));
  }

  @Override
  @Transactional
  public B2bClientResponse createClient(B2bClientCreateRequest request) {
    if (repository.existsByClientId(request.clientId())) {
      throw new BusinessException("CLIENT_ALREADY_EXISTS", "Client ID already exists: " + request.clientId(), HttpStatus.CONFLICT);
    }
    Instant now = Instant.now();
    int rpm = (request.rateLimitRpm() != null && request.rateLimitRpm() > 0) ? request.rateLimitRpm() : 120;
    B2bClientApplicationEntity entity = B2bClientApplicationEntity.create(
        UUID.randomUUID(),
        request.clientId(),
        request.clientName(),
        request.organizationTaxCode(),
        request.allowedScopes(),
        request.publicKeyPem(),
        request.clientCertThumbprintSha256(),
        request.webhookCallbackUrl(),
        request.webhookSecret(),
        rpm,
        now
    );
    entity = repository.save(entity);
    return toResponse(entity);
  }

  @Override
  @Transactional
  public B2bClientResponse updateClient(String clientId, B2bClientUpdateRequest request) {
    B2bClientApplicationEntity entity = repository.findByClientId(clientId)
        .orElseThrow(() -> new BusinessException("CLIENT_NOT_FOUND", "B2B client not found: " + clientId, HttpStatus.NOT_FOUND));

    if (request.clientName() != null && !request.clientName().isBlank()) {
      entity.setClientName(request.clientName().trim());
    }
    if (request.organizationTaxCode() != null && !request.organizationTaxCode().isBlank()) {
      entity.setOrganizationTaxCode(request.organizationTaxCode().trim());
    }
    if (request.status() != null && !request.status().isBlank()) {
      entity.setStatus(request.status().trim().toUpperCase());
    }
    if (request.allowedScopes() != null) {
      entity.setAllowedScopes(request.allowedScopes().trim());
    }
    if (request.publicKeyPem() != null) {
      entity.setPublicKeyPem(request.publicKeyPem().trim());
    }
    if (request.clientCertThumbprintSha256() != null) {
      entity.setClientCertThumbprintSha256(request.clientCertThumbprintSha256().trim());
    }
    if (request.webhookCallbackUrl() != null) {
      entity.setWebhookCallbackUrl(request.webhookCallbackUrl().trim());
    }
    if (request.webhookSecret() != null) {
      entity.setWebhookSecret(request.webhookSecret().trim());
    }
    if (request.rateLimitRpm() != null && request.rateLimitRpm() > 0) {
      entity.setRateLimitRpm(request.rateLimitRpm());
    }
    entity.setUpdatedAt(Instant.now());
    entity = repository.save(entity);
    return toResponse(entity);
  }

  @Override
  @Transactional
  public void deleteClient(String clientId) {
    B2bClientApplicationEntity entity = repository.findByClientId(clientId)
        .orElseThrow(() -> new BusinessException("CLIENT_NOT_FOUND", "B2B client not found: " + clientId, HttpStatus.NOT_FOUND));
    repository.delete(entity);
  }

  private B2bClientResponse toResponse(B2bClientApplicationEntity e) {
    return new B2bClientResponse(
        e.getId(),
        e.getClientId(),
        e.getClientName(),
        e.getOrganizationTaxCode(),
        e.getStatus(),
        e.getAllowedGrantTypes(),
        e.getAllowedScopes(),
        e.getTokenEndpointAuthMethod(),
        e.getJwksUri(),
        e.getPublicKeyPem(),
        e.getClientCertThumbprintSha256(),
        e.getWebhookCallbackUrl(),
        e.getRateLimitRpm(),
        e.getCreatedAt(),
        e.getUpdatedAt()
    );
  }
}
