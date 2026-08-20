package com.banksystem.transaction.application.virtualaccount;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.api.dto.VirtualAccountDtos.ProvisionVirtualAccountRequest;
import com.banksystem.transaction.api.dto.VirtualAccountDtos.VirtualAccountResponse;
import com.banksystem.transaction.domain.virtualaccount.VirtualAccountEntity;
import com.banksystem.transaction.domain.virtualaccount.VirtualAccountMode;
import com.banksystem.transaction.domain.virtualaccount.VirtualAccountRepository;
import com.banksystem.transaction.domain.virtualaccount.VirtualAccountStatus;
import com.banksystem.transaction.infrastructure.va.VirtualAccountProvider;
import com.banksystem.transaction.infrastructure.va.VirtualAccountProvider.ProvisionedVirtualAccount;
import com.banksystem.transaction.infrastructure.va.VirtualAccountProvider.VirtualAccountCloseRequest;
import com.banksystem.transaction.infrastructure.va.VirtualAccountProvider.VirtualAccountProvisionRequest;
import com.banksystem.transaction.infrastructure.va.VirtualAccountProviderRegistry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VirtualAccountService {

  private static final Logger log = LoggerFactory.getLogger(VirtualAccountService.class);

  private final VirtualAccountRepository virtualAccountRepository;
  private final VirtualAccountProviderRegistry providerRegistry;

  public VirtualAccountService(
      VirtualAccountRepository virtualAccountRepository,
      VirtualAccountProviderRegistry providerRegistry) {
    this.virtualAccountRepository = virtualAccountRepository;
    this.providerRegistry = providerRegistry;
  }

  @Transactional
  public VirtualAccountResponse provision(UUID organizationId, ProvisionVirtualAccountRequest request) {
    String providerCode = request.provider() != null ? request.provider() : "MOCK";
    VirtualAccountProvider provider = providerRegistry.getProvider(providerCode);

    // Reuse existing active FIXED_PAYER VA if requested with same customerReference
    if (request.mode() == VirtualAccountMode.FIXED_PAYER && request.customerReference() != null && !request.customerReference().isBlank()) {
      List<VirtualAccountEntity> existing = virtualAccountRepository.findByOrganizationId(organizationId);
      for (VirtualAccountEntity va : existing) {
        if (va.getMode() == VirtualAccountMode.FIXED_PAYER
            && request.customerReference().equals(va.getCustomerReference())
            && va.getStatus() == VirtualAccountStatus.ACTIVE) {
          log.info("[VA-SERVICE] Reusing existing FIXED_PAYER VA id={} for ref={}", va.getId(), request.customerReference());
          return toResponse(va);
        }
      }
    }

    Instant now = Instant.now();
    ProvisionedVirtualAccount provisioned = provider.provision(new VirtualAccountProvisionRequest(
        organizationId, request.bankBin(), request.parentAccountId(), request.mode(),
        request.customerReference(), request.expiresAt()
    ));

    VirtualAccountEntity entity = VirtualAccountEntity.create(
        organizationId,
        provisioned.provider(),
        provisioned.bankBin(),
        provisioned.accountNumber(),
        request.parentAccountId(),
        request.mode(),
        request.customerReference(),
        request.expiresAt(),
        now
    );
    virtualAccountRepository.save(entity);

    log.info("[VA-SERVICE] Created VA id={}, num={}, provider={}, org={}",
        entity.getId(), entity.getAccountNumber(), entity.getProvider(), organizationId);

    return toResponse(entity);
  }

  @Transactional
  public void close(UUID organizationId, UUID vaId) {
    VirtualAccountEntity va = virtualAccountRepository.findById(vaId).orElseThrow(() ->
        new BusinessException("VA_NOT_FOUND", "Virtual account not found"));

    if (!va.getOrganizationId().equals(organizationId)) {
      throw new BusinessException("FORBIDDEN", "Unauthorized access to virtual account");
    }

    VirtualAccountProvider provider = providerRegistry.getProvider(va.getProvider());
    provider.close(new VirtualAccountCloseRequest(va.getProvider(), va.getBankBin(), va.getAccountNumber()));

    va.setStatus(VirtualAccountStatus.CLOSED);
    va.setUpdatedAt(Instant.now());
    virtualAccountRepository.save(va);
    log.info("[VA-SERVICE] Closed VA id={}", vaId);
  }

  @Transactional(readOnly = true)
  public VirtualAccountResponse getById(UUID organizationId, UUID vaId) {
    VirtualAccountEntity va = virtualAccountRepository.findById(vaId).orElseThrow(() ->
        new BusinessException("VA_NOT_FOUND", "Virtual account not found"));

    if (organizationId != null && !va.getOrganizationId().equals(organizationId)) {
      throw new BusinessException("FORBIDDEN", "Unauthorized access to virtual account");
    }
    return toResponse(va);
  }

  @Transactional(readOnly = true)
  public Page<VirtualAccountResponse> search(VirtualAccountSearchQuery query) {
    PageRequest pageable = PageRequest.of(query.page(), query.size());
    return virtualAccountRepository.search(query.organizationId(), query.q(), query.status(), pageable)
        .map(this::toResponse);
  }

  @Transactional(readOnly = true)
  public Page<VirtualAccountResponse> search(UUID organizationId, String q, VirtualAccountStatus status, Pageable pageable) {
    return virtualAccountRepository.search(organizationId, q, status, pageable).map(this::toResponse);
  }

  public VirtualAccountResponse toResponse(VirtualAccountEntity va) {
    String qrUrl = String.format("https://img.vietqr.io/image/%s-%s-compact2.png", va.getBankBin(), va.getAccountNumber());
    return new VirtualAccountResponse(
        va.getId(),
        va.getOrganizationId(),
        va.getProvider(),
        va.getBankBin(),
        va.getAccountNumber(),
        va.getParentAccountId(),
        va.getMode(),
        va.getCustomerReference(),
        va.getStatus(),
        qrUrl,
        va.getActivatedAt(),
        va.getExpiresAt(),
        va.getCreatedAt()
    );
  }
}
