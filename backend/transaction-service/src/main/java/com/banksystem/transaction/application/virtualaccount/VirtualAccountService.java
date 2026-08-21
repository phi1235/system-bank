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
        request.displayName(),
        request.expiresAt(),
        now
    );
    virtualAccountRepository.save(entity);

    log.info("[VA-SERVICE] Created VA id={}, num={}, provider={}, org={}, displayName={}",
        entity.getId(), entity.getAccountNumber(), entity.getProvider(), organizationId, entity.getDisplayName());

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
  public List<VirtualAccountResponse> searchList(UUID organizationId, String q, VirtualAccountStatus status) {
    String trimmedQ = (q != null && !q.isBlank()) ? q.trim() : "";
    boolean hasOrgId = organizationId != null;
    boolean hasQ = (q != null && !q.isBlank());
    boolean hasStatus = status != null;
    return virtualAccountRepository.searchList(
        hasOrgId, organizationId != null ? organizationId : UUID.randomUUID(),
        hasQ, trimmedQ,
        hasStatus, status != null ? status : VirtualAccountStatus.ACTIVE
    ).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public Page<VirtualAccountResponse> search(VirtualAccountSearchQuery query) {
    PageRequest pageable = PageRequest.of(query.page(), query.size());
    String trimmedQ = (query.q() != null && !query.q().isBlank()) ? query.q().trim() : "";
    boolean hasOrgId = query.organizationId() != null;
    boolean hasQ = (query.q() != null && !query.q().isBlank());
    boolean hasStatus = query.status() != null;
    return virtualAccountRepository.search(
        hasOrgId, query.organizationId() != null ? query.organizationId() : UUID.randomUUID(),
        hasQ, trimmedQ,
        hasStatus, query.status() != null ? query.status() : VirtualAccountStatus.ACTIVE,
        pageable
    ).map(this::toResponse);
  }

  @Transactional(readOnly = true)
  public Page<VirtualAccountResponse> search(UUID organizationId, String q, VirtualAccountStatus status, Pageable pageable) {
    String trimmedQ = (q != null && !q.isBlank()) ? q.trim() : "";
    boolean hasOrgId = organizationId != null;
    boolean hasQ = (q != null && !q.isBlank());
    boolean hasStatus = status != null;
    return virtualAccountRepository.search(
        hasOrgId, organizationId != null ? organizationId : UUID.randomUUID(),
        hasQ, trimmedQ,
        hasStatus, status != null ? status : VirtualAccountStatus.ACTIVE,
        pageable
    ).map(this::toResponse);
  }

  public VirtualAccountResponse toResponse(VirtualAccountEntity va) {
    String qrUrl;
    if (va.getDisplayName() != null && !va.getDisplayName().isBlank()) {
      String encodedName = java.net.URLEncoder.encode(va.getDisplayName(), java.nio.charset.StandardCharsets.UTF_8);
      qrUrl = String.format("https://img.vietqr.io/image/%s-%s-compact2.png?accountName=%s", va.getBankBin(), va.getAccountNumber(), encodedName);
    } else {
      qrUrl = String.format("https://img.vietqr.io/image/%s-%s-compact2.png", va.getBankBin(), va.getAccountNumber());
    }
    return new VirtualAccountResponse(
        va.getId(),
        va.getOrganizationId(),
        va.getProvider(),
        va.getBankBin(),
        va.getAccountNumber(),
        va.getParentAccountId(),
        va.getMode(),
        va.getCustomerReference(),
        va.getDisplayName(),
        va.getStatus(),
        qrUrl,
        va.getActivatedAt(),
        va.getExpiresAt(),
        va.getCreatedAt()
    );
  }
}

