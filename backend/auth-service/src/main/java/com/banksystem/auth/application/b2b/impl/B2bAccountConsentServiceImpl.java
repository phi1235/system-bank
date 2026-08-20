package com.banksystem.auth.application.b2b.impl;

import com.banksystem.auth.api.dto.B2bDtos.B2bConsentCreateRequest;
import com.banksystem.auth.api.dto.B2bDtos.B2bConsentResponse;
import com.banksystem.auth.application.b2b.B2bAccountConsentService;
import com.banksystem.auth.application.b2b.query.B2bConsentSearchQuery;
import com.banksystem.auth.domain.b2b.B2bAccountConsentEntity;
import com.banksystem.auth.domain.b2b.B2bAccountConsentRepository;
import com.banksystem.auth.domain.b2b.B2bClientApplicationRepository;
import com.banksystem.common.exception.BusinessException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class B2bAccountConsentServiceImpl implements B2bAccountConsentService {

  private final B2bAccountConsentRepository consentRepository;
  private final B2bClientApplicationRepository clientRepository;

  public B2bAccountConsentServiceImpl(
      B2bAccountConsentRepository consentRepository,
      B2bClientApplicationRepository clientRepository) {
    this.consentRepository = consentRepository;
    this.clientRepository = clientRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public Page<B2bConsentResponse> listConsents(B2bConsentSearchQuery query) {
    return consentRepository.searchConsents(
        query.clientId(),
        query.customerId(),
        query.status(),
        query.accountNumber(),
        query.toPageable()
    ).map(this::toResponse);
  }

  @Override
  @Transactional(readOnly = true)
  public B2bConsentResponse getConsent(UUID consentId) {
    return consentRepository.findById(consentId)
        .map(this::toResponse)
        .orElseThrow(() -> new BusinessException("CONSENT_NOT_FOUND", "Account consent not found: " + consentId, HttpStatus.NOT_FOUND));
  }

  @Override
  @Transactional
  public B2bConsentResponse grantConsent(B2bConsentCreateRequest request) {
    if (!clientRepository.existsByClientId(request.clientId())) {
      throw new BusinessException("CLIENT_NOT_FOUND", "B2B client not found: " + request.clientId(), HttpStatus.NOT_FOUND);
    }

    // If an existing active consent exists for this client + account, update or reuse
    Optional<B2bAccountConsentEntity> existing = consentRepository.findByClientIdAndAccountNumber(
        request.clientId(), request.accountNumber());

    Instant now = Instant.now();
    UUID customerId = request.customerId() != null ? request.customerId() : UUID.randomUUID();
    B2bAccountConsentEntity entity;
    if (existing.isPresent()) {
      entity = existing.get();
      entity.setPermissions(request.permissions() != null ? request.permissions() : entity.getPermissions());
      entity.setStatus("AUTHORISED");
      entity.setValidUntil(request.validUntil() != null ? request.validUntil() : now.plusSeconds(86400 * 365));
    } else {
      entity = B2bAccountConsentEntity.create(
          UUID.randomUUID(),
          request.clientId(),
          request.accountNumber(),
          customerId,
          request.permissions(),
          request.validUntil(),
          now
      );
    }
    entity = consentRepository.save(entity);
    return toResponse(entity);
  }

  @Override
  @Transactional
  public B2bConsentResponse revokeConsent(UUID consentId) {
    B2bAccountConsentEntity entity = consentRepository.findById(consentId)
        .orElseThrow(() -> new BusinessException("CONSENT_NOT_FOUND", "Account consent not found: " + consentId, HttpStatus.NOT_FOUND));
    entity.setStatus("REVOKED");
    entity = consentRepository.save(entity);
    return toResponse(entity);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean verifyAccountAccess(String clientId, String accountNumber, String requiredPermission) {
    if (clientId == null || accountNumber == null) {
      return false;
    }
    return consentRepository.findByClientIdAndAccountNumberAndStatus(clientId, accountNumber, "AUTHORISED")
        .filter(c -> c.isValid(Instant.now()))
        .filter(c -> requiredPermission == null || c.permissionList().contains(requiredPermission) || c.permissionList().contains("*"))
        .isPresent();
  }

  private B2bConsentResponse toResponse(B2bAccountConsentEntity e) {
    return new B2bConsentResponse(
        e.getId(),
        e.getClientId(),
        e.getAccountNumber(),
        e.getCustomerId(),
        e.getPermissions(),
        e.getStatus(),
        e.getValidUntil(),
        e.getCreatedAt()
    );
  }
}
