package com.banksystem.customer.application.command;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.customer.api.dto.CustomerDtos.CreateProfileRequest;
import com.banksystem.customer.api.dto.CustomerDtos.CustomerResponse;
import com.banksystem.customer.api.dto.CustomerDtos.KycUpdateRequest;
import com.banksystem.customer.api.dto.CustomerDtos.UpdateProfileRequest;
import com.banksystem.customer.application.OpsAlertPublisher;
import com.banksystem.customer.application.mapper.CustomerMapper;
import com.banksystem.customer.application.security.CustomerCryptoService;
import com.banksystem.customer.domain.CustomerEntity;
import com.banksystem.customer.domain.CustomerRepository;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerCommandService {

  private static final Set<String> KYC_STATUSES = Set.of("PENDING", "VERIFIED", "REJECTED");

  private final CustomerRepository repository;
  private final OpsAlertPublisher opsAlertPublisher;
  private final CustomerMapper mapper;
  private final CustomerCryptoService cryptoService;

  public CustomerCommandService(
      CustomerRepository repository,
      OpsAlertPublisher opsAlertPublisher,
      CustomerMapper mapper,
      CustomerCryptoService cryptoService) {
    this.repository = repository;
    this.opsAlertPublisher = opsAlertPublisher;
    this.mapper = mapper;
    this.cryptoService = cryptoService;
  }

  @Transactional
  public CustomerResponse create(UUID userId, CreateProfileRequest req) {
    if (repository.existsById(userId)) {
      throw new BusinessException("PROFILE_EXISTS", "Customer profile already exists");
    }
    CustomerEntity e = new CustomerEntity();
    e.setId(userId);
    e.setFullName(req.fullName().trim());
    e.setPhone(req.phone());
    e.setEmail(req.email() == null ? null : req.email().trim().toLowerCase());
    e.setAddress(req.address());
    e.setKycStatus("PENDING");
    if (req.nationalId() != null && !req.nationalId().isBlank()) {
      e.setNationalIdEncrypted(cryptoService.encryptNationalId(req.nationalId()));
    }
    e.setCreatedAt(Instant.now());
    e.setUpdatedAt(Instant.now());
    CustomerEntity saved = repository.save(e);
    opsAlertPublisher.kycUpdated(saved, null);
    return toResponse(saved);
  }

  @Transactional
  public CustomerResponse updateMe(UUID userId, UpdateProfileRequest req) {
    CustomerEntity e = require(userId);
    if (req.fullName() != null && !req.fullName().isBlank()) {
      e.setFullName(req.fullName().trim());
    }
    if (req.phone() != null) {
      e.setPhone(req.phone());
    }
    if (req.email() != null) {
      e.setEmail(req.email().isBlank() ? null : req.email().trim().toLowerCase());
    }
    if (req.address() != null) {
      e.setAddress(req.address());
    }
    e.setUpdatedAt(Instant.now());
    return toResponse(repository.save(e));
  }

  @Transactional
  public CustomerResponse updateKyc(UUID id, KycUpdateRequest req) {
    if (!KYC_STATUSES.contains(req.kycStatus())) {
      throw new BusinessException("INVALID_KYC_STATUS", "kycStatus must be PENDING|VERIFIED|REJECTED");
    }
    CustomerEntity e = require(id);
    String previous = e.getKycStatus();
    if (req.kycStatus().equals(previous)) {
      return toResponse(e);
    }
    e.setKycStatus(req.kycStatus());
    e.setUpdatedAt(Instant.now());
    CustomerEntity saved = repository.save(e);
    opsAlertPublisher.kycUpdated(saved, previous);
    return toResponse(saved);
  }

  private CustomerEntity require(UUID id) {
    return repository.findById(id)
        .orElseThrow(() -> new BusinessException("CUSTOMER_NOT_FOUND", "Customer not found"));
  }

  private CustomerResponse toResponse(CustomerEntity e) {
    String masked = cryptoService.decryptAndMaskNationalId(e.getNationalIdEncrypted());
    return mapper.toResponse(e, masked);
  }
}
