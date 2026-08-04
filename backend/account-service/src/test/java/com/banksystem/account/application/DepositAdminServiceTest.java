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
import com.banksystem.account.application.gateway.AuditGateway;
import com.banksystem.account.application.gateway.CustomerGateway;
import com.banksystem.account.domain.DepositProductEntity;
import com.banksystem.account.domain.DepositProductRepository;
import com.banksystem.account.domain.TermDepositRepository;
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
  private AuditGateway auditGateway;
  private DepositAdminService service;

  private final UUID actor = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    productRepository = mock(DepositProductRepository.class);
    auditGateway = mock(AuditGateway.class);
    service =
        new DepositAdminService(
            mock(DepositReportMapper.class),
            mock(TermDepositRepository.class),
            productRepository,
            mock(com.banksystem.account.domain.AccountRepository.class),
            auditGateway,
            mock(CustomerGateway.class),
            Clock.fixed(Instant.parse("2026-07-27T03:00:00Z"), ZoneOffset.UTC),
            "Asia/Bangkok");
  }

  @Test
  void updateProductAppliesOnlyProvidedFieldsAndAudits() {
    DepositProductEntity product = product();
    when(productRepository.findById("TD6M")).thenReturn(Optional.of(product));

    DepositProductResponse res =
        service.updateProduct("TD6M", new UpdateDepositProductRequest(650, null, null, null), actor);

    assertEquals(650, res.rateBps());
    verify(productRepository).save(product);
    verify(auditGateway)
        .recordAuditLog(eq(actor), eq("DEPOSIT_PRODUCT_UPDATE"), eq("DEPOSIT_PRODUCT"), eq("TD6M"), anyString());
  }

  @Test
  void updateProductThrowsWhenNotFound() {
    when(productRepository.findById("MISSING")).thenReturn(Optional.empty());

    assertThrows(
        BusinessException.class,
        () -> service.updateProduct("MISSING", new UpdateDepositProductRequest(100, 100, null, null), actor));
  }

  private static DepositProductEntity product() {
    DepositProductEntity p = new DepositProductEntity();
    p.setCode("TD6M");
    p.setTenorMonths(6);
    p.setRateBps(600);
    p.setEarlyRateBps(50);
    p.setMinAmount(new BigDecimal("1000000.00"));
    p.setActive(true);
    return p;
  }
}
