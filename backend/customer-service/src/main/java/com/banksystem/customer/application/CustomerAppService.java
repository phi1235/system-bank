package com.banksystem.customer.application;

import com.banksystem.common.api.PageResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.common.security.CryptoUtils;
import com.banksystem.customer.api.dto.CustomerDtos.CreateProfileRequest;
import com.banksystem.customer.api.dto.CustomerDtos.CustomerResponse;
import com.banksystem.customer.api.dto.CustomerDtos.KycUpdateRequest;
import com.banksystem.customer.api.dto.CustomerDtos.UpdateProfileRequest;
import com.banksystem.customer.domain.CustomerEntity;
import com.banksystem.customer.domain.CustomerRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerAppService {

  private static final Set<String> KYC = Set.of("PENDING", "VERIFIED", "REJECTED");

  private final CustomerRepository repository;
  private final OpsAlertPublisher opsAlertPublisher;
  private final String aesKey;

  public CustomerAppService(
      CustomerRepository repository,
      OpsAlertPublisher opsAlertPublisher,
      @Value("${bank.aes.secret-key}") String aesKey) {
    this.repository = repository;
    this.opsAlertPublisher = opsAlertPublisher;
    this.aesKey = aesKey;
  }

  @Transactional
  public CustomerResponse create(UUID userId, CreateProfileRequest req) {
    if (repository.existsById(userId)) {
      throw new BusinessException("PROFILE_EXISTS", "Customer profile already exists", HttpStatus.CONFLICT);
    }
    CustomerEntity e = new CustomerEntity();
    e.setId(userId);
    e.setFullName(req.fullName().trim());
    e.setPhone(req.phone());
    e.setEmail(req.email() == null ? null : req.email().trim().toLowerCase());
    e.setAddress(req.address());
    e.setKycStatus("PENDING");
    if (req.nationalId() != null && !req.nationalId().isBlank()) {
      e.setNationalIdEncrypted(CryptoUtils.encrypt(req.nationalId().trim(), aesKey));
    }
    e.setCreatedAt(Instant.now());
    e.setUpdatedAt(Instant.now());
    CustomerEntity saved = repository.save(e);
    // New profile starts PENDING — surface to ops for review.
    opsAlertPublisher.kycUpdated(saved, null);
    return toResponse(saved);
  }

  @Transactional(readOnly = true)
  public CustomerResponse getMe(UUID userId) {
    return toResponse(require(userId));
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
      // blank clears email; non-blank normalized like create()
      e.setEmail(req.email().isBlank() ? null : req.email().trim().toLowerCase());
    }
    if (req.address() != null) {
      e.setAddress(req.address());
    }
    e.setUpdatedAt(Instant.now());
    return toResponse(repository.save(e));
  }

  @Transactional(readOnly = true)
  public PageResponse<CustomerResponse> list(String q, String kycStatus, int page, int size) {
    String qTrim = q == null ? "" : q.trim();
    boolean hasQ = !qTrim.isEmpty();
    String kyc = kycStatus == null ? "" : kycStatus.trim().toUpperCase();
    boolean hasKyc = !kyc.isEmpty();
    if (hasKyc && !KYC.contains(kyc)) {
      throw new BusinessException(
          "INVALID_KYC_STATUS",
          "kycStatus must be PENDING|VERIFIED|REJECTED",
          HttpStatus.BAD_REQUEST);
    }
    int p = Math.max(page, 0);
    int s = size < 1 ? 20 : Math.min(size, 100);
    Page<CustomerEntity> result =
        repository.search(hasQ, hasQ ? qTrim : "", hasKyc, hasKyc ? kyc : "PENDING", PageRequest.of(p, s));
    List<CustomerResponse> items = result.getContent().stream().map(this::toResponse).toList();
    return new PageResponse<>(
        items,
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages());
  }

  @Transactional
  public CustomerResponse updateKyc(UUID id, KycUpdateRequest req) {
    if (!KYC.contains(req.kycStatus())) {
      throw new BusinessException("INVALID_KYC_STATUS", "kycStatus must be PENDING|VERIFIED|REJECTED",
          HttpStatus.BAD_REQUEST);
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

  @Transactional(readOnly = true)
  public boolean exists(UUID id) {
    return repository.existsById(id);
  }

  private CustomerEntity require(UUID id) {
    return repository.findById(id)
        .orElseThrow(() -> new BusinessException("CUSTOMER_NOT_FOUND", "Customer not found",
            HttpStatus.NOT_FOUND));
  }

  private CustomerResponse toResponse(CustomerEntity e) {
    String masked = null;
    if (e.getNationalIdEncrypted() != null) {
      try {
        String plain = CryptoUtils.decrypt(e.getNationalIdEncrypted(), aesKey);
        masked = CryptoUtils.maskNationalId(plain);
      } catch (Exception ex) {
        masked = "****";
      }
    }
    return new CustomerResponse(
        e.getId().toString(),
        e.getFullName(),
        e.getPhone(),
        e.getEmail(),
        masked,
        e.getKycStatus(),
        e.getAddress()
    );
  }
}
