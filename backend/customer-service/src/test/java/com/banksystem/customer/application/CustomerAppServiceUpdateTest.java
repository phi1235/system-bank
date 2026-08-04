package com.banksystem.customer.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banksystem.customer.api.dto.CustomerDtos.KycUpdateRequest;
import com.banksystem.customer.api.dto.CustomerDtos.UpdateProfileRequest;
import com.banksystem.customer.application.command.CustomerCommandService;
import com.banksystem.customer.application.mapper.CustomerMapper;
import com.banksystem.customer.application.security.CustomerCryptoService;
import com.banksystem.customer.domain.CustomerEntity;
import com.banksystem.customer.domain.CustomerRepository;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CustomerAppServiceUpdateTest {

  private static final String AES_KEY =
      Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

  private CustomerRepository repository;
  private OpsAlertPublisher opsAlertPublisher;
  private CustomerCommandService service;
  private final UUID userId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

  @BeforeEach
  void setUp() {
    repository = mock(CustomerRepository.class);
    opsAlertPublisher = mock(OpsAlertPublisher.class);
    CustomerCryptoService cryptoService = new CustomerCryptoService(AES_KEY);
    CustomerMapper mapper = new CustomerMapper();
    service = new CustomerCommandService(repository, opsAlertPublisher, mapper, cryptoService);
  }

  @Test
  void updateMe_updatesAndNormalizesEmail() {
    CustomerEntity existing = baseEntity();
    existing.setEmail("old@example.com");
    when(repository.findById(userId)).thenReturn(Optional.of(existing));
    when(repository.save(any(CustomerEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    var res = service.updateMe(
        userId,
        new UpdateProfileRequest("Alice Updated", "0901111222", "  Alice@Bank.VN ", "Hanoi"));

    assertEquals("Alice Updated", res.fullName());
    assertEquals("0901111222", res.phone());
    assertEquals("alice@bank.vn", res.email());
    assertEquals("Hanoi", res.address());

    ArgumentCaptor<CustomerEntity> cap = ArgumentCaptor.forClass(CustomerEntity.class);
    verify(repository).save(cap.capture());
    assertEquals("alice@bank.vn", cap.getValue().getEmail());
  }

  @Test
  void updateMe_blankEmailClearsField() {
    CustomerEntity existing = baseEntity();
    existing.setEmail("old@example.com");
    when(repository.findById(userId)).thenReturn(Optional.of(existing));
    when(repository.save(any(CustomerEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    var res = service.updateMe(
        userId,
        new UpdateProfileRequest(null, null, "   ", null));

    assertNull(res.email());
  }

  @Test
  void updateKyc_publishesOpsAlertOnTransition() {
    CustomerEntity existing = baseEntity();
    existing.setKycStatus("PENDING");
    when(repository.findById(userId)).thenReturn(Optional.of(existing));
    when(repository.save(any(CustomerEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    var res = service.updateKyc(userId, new KycUpdateRequest("VERIFIED"));

    assertEquals("VERIFIED", res.kycStatus());
    verify(opsAlertPublisher).kycUpdated(any(CustomerEntity.class), eq("PENDING"));
  }

  @Test
  void updateKyc_sameStatusNoOpsAlert() {
    CustomerEntity existing = baseEntity();
    existing.setKycStatus("VERIFIED");
    when(repository.findById(userId)).thenReturn(Optional.of(existing));

    var res = service.updateKyc(userId, new KycUpdateRequest("VERIFIED"));

    assertEquals("VERIFIED", res.kycStatus());
    verify(repository, never()).save(any());
    verify(opsAlertPublisher, never()).kycUpdated(any(), any());
  }

  private CustomerEntity baseEntity() {
    CustomerEntity e = new CustomerEntity();
    e.setId(userId);
    e.setFullName("Alice");
    e.setPhone("0901111222");
    e.setKycStatus("PENDING");
    return e;
  }
}
