package com.banksystem.customer.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banksystem.common.api.PageResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.customer.api.dto.CustomerDtos.CustomerResponse;
import com.banksystem.customer.application.mapper.CustomerMapper;
import com.banksystem.customer.application.query.CustomerQueryService;
import com.banksystem.customer.application.query.CustomerSearchQuery;
import com.banksystem.customer.application.security.CustomerCryptoService;
import com.banksystem.customer.domain.CustomerEntity;
import com.banksystem.customer.domain.CustomerRepository;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class CustomerAppServiceListTest {

  private static final String AES_KEY =
      Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

  private CustomerRepository repository;
  private CustomerQueryService service;

  @BeforeEach
  void setUp() {
    repository = mock(CustomerRepository.class);
    CustomerCryptoService cryptoService = new CustomerCryptoService(AES_KEY);
    CustomerMapper mapper = new CustomerMapper();
    service = new CustomerQueryService(repository, mapper, cryptoService);
  }

  @Test
  void list_passesSearchFlags() {
    CustomerEntity e = new CustomerEntity();
    e.setId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
    e.setFullName("Alice");
    e.setKycStatus("PENDING");
    when(repository.search(
            eq(true),
            eq("ali"),
            eq(true),
            eq("PENDING"),
            any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(e), PageRequest.of(0, 20), 1));

    PageResponse<CustomerResponse> page = service.list(CustomerSearchQuery.of("  ali  ", "pending", 0, 20));

    assertEquals(1, page.items().size());
    assertEquals("Alice", page.items().get(0).fullName());
    assertEquals("PENDING", page.items().get(0).kycStatus());
    verify(repository)
        .search(eq(true), eq("ali"), eq(true), eq("PENDING"), any(Pageable.class));
  }

  @Test
  void list_rejectsInvalidKycStatus() {
    BusinessException ex =
        assertThrows(
            BusinessException.class,
            () -> service.list(CustomerSearchQuery.of(null, "INVALID", 0, 20)));
    assertEquals("INVALID_KYC_STATUS", ex.getCode());
  }
}
