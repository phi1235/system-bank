package com.banksystem.account.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banksystem.account.api.dto.DepositDtos.DepositProductResponse;
import com.banksystem.account.api.dto.DepositDtos.UpdateDepositProductRequest;
import com.banksystem.account.domain.DepositProductEntity;
import com.banksystem.account.domain.DepositProductRepository;
import com.banksystem.account.domain.TermDepositRepository;
import com.banksystem.account.infrastructure.feign.AuditClient;
import com.banksystem.account.infrastructure.mybatis.DepositReportMapper;
import com.banksystem.common.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DepositAdminServiceTest {

  private DepositProductRepository productRepository;
  private AuditClient auditClient;
  private DepositAdminService service;

  private final UUID actor = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    productRepository = mock(DepositProductRepository.class);
    auditClient = mock(AuditClient.class);
    service =
        new DepositAdminService(
            mock(DepositReportMapper.class),
            mock(TermDepositRepository.class),
            productRepository,
            mock(com.banksystem.account.domain.AccountRepository.class),
            auditClient,
            mock(com.banksystem.account.infrastructure.feign.CustomerClient.class),
            "test-key",
            "customer-key",
            Clock.fixed(Instant.parse("2026-07-27T03:00:00Z"), ZoneOffset.UTC),
            "Asia/Bangkok");
  }

  @Test
  void updateProductAppliesOnlyProvidedFieldsAndAudits() {
    DepositProductEntity product = product();
    when(productRepository.findById("TD6M")).thenReturn(Optional.of(product));

    DepositProductResponse res =
        service.updateProduct(
            "TD6M", new UpdateDepositProductRequest(500, null, null, null), actor);

    assertEquals(500, res.rateBps());
    // untouched fields keep old values
    assertEquals(50, res.earlyRateBps());
    assertEquals(0, res.minAmount().compareTo(new BigDecimal("1000000")));
    verify(productRepository).save(product);
    verify(auditClient)
        .createAuditLog(
            any(AuditClient.CreateAuditLogRequest.class), eq("test-key"));
  }

  @Test
  void updateUnknownProductRejects() {
    when(productRepository.findById("NOPE")).thenReturn(Optional.empty());
    BusinessException ex =
        assertThrows(
            BusinessException.class,
            () ->
                service.updateProduct(
                    "NOPE", new UpdateDepositProductRequest(500, null, null, null), actor));
    assertEquals("DEPOSIT_PRODUCT_NOT_FOUND", ex.getCode());
  }

  @Test
  void auditFailureNeverFailsTheRateChange() {
    DepositProductEntity product = product();
    when(productRepository.findById("TD6M")).thenReturn(Optional.of(product));
    when(auditClient.createAuditLog(any(), anyString()))
        .thenThrow(new RuntimeException("transaction-service down"));

    DepositProductResponse res =
        service.updateProduct(
            "TD6M", new UpdateDepositProductRequest(null, null, null, false), actor);

    assertEquals(false, res.active());
  }

  private static DepositProductEntity product() {
    DepositProductEntity p = new DepositProductEntity();
    p.setCode("TD6M");
    p.setTenorMonths(6);
    p.setRateBps(460);
    p.setEarlyRateBps(50);
    p.setMinAmount(new BigDecimal("1000000"));
    p.setActive(true);
    return p;
  }
}
