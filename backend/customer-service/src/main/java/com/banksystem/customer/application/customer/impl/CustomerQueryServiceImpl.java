package com.banksystem.customer.application.customer.impl;

import com.banksystem.common.api.PageResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.customer.api.dto.CustomerDtos.CustomerNameResponse;
import com.banksystem.customer.api.dto.CustomerDtos.CustomerResponse;
import com.banksystem.customer.api.dto.CustomerDtos.CustomerSearchFilterRequest;
import com.banksystem.customer.application.customer.CustomerContactResult;
import com.banksystem.customer.application.customer.CustomerQueryService;
import com.banksystem.customer.application.customer.CustomerSearchQuery;
import com.banksystem.customer.application.mapper.CustomerMapper;
import com.banksystem.customer.application.security.CustomerCryptoService;
import com.banksystem.customer.domain.customer.CustomerEntity;
import com.banksystem.customer.domain.customer.CustomerRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerQueryServiceImpl implements CustomerQueryService {

  private final CustomerRepository repository;
  private final CustomerMapper mapper;
  private final CustomerCryptoService cryptoService;

  public CustomerQueryServiceImpl(
      CustomerRepository repository,
      CustomerMapper mapper,
      CustomerCryptoService cryptoService) {
    this.repository = repository;
    this.mapper = mapper;
    this.cryptoService = cryptoService;
  }

  @Transactional(readOnly = true)
  public CustomerResponse getMe(UUID userId) {
    return toResponse(require(userId));
  }

  @Transactional(readOnly = true)
  public List<CustomerNameResponse> namesByIds(List<UUID> userIds) {
    return repository.findAllById(userIds).stream()
        .map(c -> new CustomerNameResponse(c.getId().toString(), c.getFullName()))
        .toList();
  }

  @Transactional(readOnly = true)
  public PageResponse<CustomerResponse> list(CustomerSearchFilterRequest req) {
    return list(CustomerSearchQuery.of(req));
  }

  @Transactional(readOnly = true)
  public PageResponse<CustomerResponse> list(CustomerSearchQuery query) {
    Page<CustomerEntity> result = repository.search(
        query.hasQ(),
        query.qNorm(),
        query.hasKyc(),
        query.kycNorm(),
        PageRequest.of(query.page(), query.size()));

    List<CustomerResponse> items = result.getContent().stream()
        .map(this::toResponse)
        .toList();

    return new PageResponse<>(
        items,
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages());
  }

  @Transactional(readOnly = true)
  public boolean exists(UUID id) {
    return repository.existsById(id);
  }

  @Override
  @Transactional(readOnly = true)
  public CustomerContactResult contact(UUID id) {
    CustomerEntity customer = require(id);
    return new CustomerContactResult(
        customer.getId().toString(), customer.getEmail(), customer.getPhone());
  }

  public CustomerEntity require(UUID id) {
    return repository.findById(id)
        .orElseThrow(() -> new BusinessException("CUSTOMER_NOT_FOUND", "Customer not found"));
  }

  private CustomerResponse toResponse(CustomerEntity e) {
    String masked = cryptoService.decryptAndMaskNationalId(e.getNationalIdEncrypted());
    return mapper.toResponse(e, masked);
  }
}
