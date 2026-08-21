package com.banksystem.auth.application.b2b;

import com.banksystem.auth.api.dto.B2bDtos.B2bConsentCreateRequest;
import com.banksystem.auth.api.dto.B2bDtos.B2bConsentResponse;
import com.banksystem.auth.application.b2b.query.B2bConsentSearchQuery;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

public interface B2bAccountConsentService {

  List<B2bConsentResponse> listConsents(String clientId, UUID customerId, String status, String accountNumber);

  Page<B2bConsentResponse> listConsents(B2bConsentSearchQuery query);

  B2bConsentResponse getConsent(UUID consentId);

  B2bConsentResponse grantConsent(B2bConsentCreateRequest request);

  B2bConsentResponse revokeConsent(UUID consentId);

  boolean verifyAccountAccess(String clientId, String accountNumber, String requiredPermission);
}
