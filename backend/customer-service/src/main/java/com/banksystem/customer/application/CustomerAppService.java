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
  private final String aesKey;

  public CustomerAppService(
      CustomerRepository repository,
      @Value("${bank.aes.secret-key}") String aesKey) {
    this.repository = repository;
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
    return toResponse(repository.save(e));
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
    if (req.address() != null) {
      e.setAddress(req.address());
    }
    e.setUpdatedAt(Instant.now());
    return toResponse(repository.save(e));
  }

  @Transactional(readOnly = true)
  public PageResponse<CustomerResponse> list(String q, int page, int size) {
    Page<CustomerEntity> p = repository.search(q, PageRequest.of(page, size));
    List<CustomerResponse> items = p.getContent().stream().map(this::toResponse).toList();
    return new PageResponse<>(items, p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
  }

  @Transactional
  public CustomerResponse updateKyc(UUID id, KycUpdateRequest req) {
    if (!KYC.contains(req.kycStatus())) {
      throw new BusinessException("INVALID_KYC_STATUS", "kycStatus must be PENDING|VERIFIED|REJECTED",
          HttpStatus.BAD_REQUEST);
    }
    CustomerEntity e = require(id);
    e.setKycStatus(req.kycStatus());
    e.setUpdatedAt(Instant.now());
    return toResponse(repository.save(e));
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
