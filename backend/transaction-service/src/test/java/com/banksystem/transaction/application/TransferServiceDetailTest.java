package com.banksystem.transaction.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.api.dto.TransferDtos.TransferDetailResponse;
import com.banksystem.transaction.config.GatewayUser;
import com.banksystem.transaction.domain.AuditLogRepository;
import com.banksystem.transaction.domain.SagaStepLogEntity;
import com.banksystem.transaction.domain.SagaStepLogRepository;
import com.banksystem.transaction.domain.TransferOrderEntity;
import com.banksystem.transaction.domain.TransferOrderRepository;
import com.banksystem.transaction.domain.TransferStatus;
import com.banksystem.transaction.infrastructure.feign.AccountClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransferServiceDetailTest {

  private TransferOrderRepository transferOrderRepository;
  private SagaStepLogRepository sagaStepLogRepository;
  private TransferService service;

  private final UUID ownerId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
  private final UUID otherId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
  private final UUID transferId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

  @BeforeEach
  void setUp() {
    transferOrderRepository = mock(TransferOrderRepository.class);
    sagaStepLogRepository = mock(SagaStepLogRepository.class);
    service = new TransferService(
        transferOrderRepository,
        mock(AuditLogRepository.class),
        sagaStepLogRepository,
        mock(AccountClient.class),
        mock(TransferSagaOrchestrator.class),
        mock(TransferLimitPolicy.class),
        mock(TransferFeePolicy.class),
        "test-key");
  }

  @Test
  void ownerSeesOrderedSagaSteps() {
    TransferOrderEntity order = sampleOrder(ownerId);
    when(transferOrderRepository.findById(transferId)).thenReturn(Optional.of(order));
    when(sagaStepLogRepository.findByTransferIdOrderByCreatedAtAsc(transferId))
        .thenReturn(List.of(
            SagaStepLogEntity.of(transferId, "DEBIT_SOURCE", "SUCCESS", "ok"),
            SagaStepLogEntity.of(transferId, "CREDIT_DEST", "SUCCESS", "ok")));

    GatewayUser user = new GatewayUser(ownerId, List.of("CUSTOMER"), List.of());
    TransferDetailResponse detail = service.getDetail(transferId, user);

    assertEquals(transferId.toString(), detail.transfer().transactionId());
    assertEquals(2, detail.steps().size());
    assertEquals("DEBIT_SOURCE", detail.steps().get(0).step());
    assertEquals("CREDIT_DEST", detail.steps().get(1).step());
  }

  @Test
  void staffWithListPermissionCanRead() {
    when(transferOrderRepository.findById(transferId)).thenReturn(Optional.of(sampleOrder(ownerId)));
    when(sagaStepLogRepository.findByTransferIdOrderByCreatedAtAsc(transferId)).thenReturn(List.of());

    GatewayUser staff = new GatewayUser(otherId, List.of("OPS_ADMIN"), List.of("transactions:list:view"));
    TransferDetailResponse detail = service.getDetail(transferId, staff);
    assertEquals("COMPLETED", detail.transfer().status());
    assertEquals(0, detail.steps().size());
  }

  @Test
  void strangerForbidden() {
    when(transferOrderRepository.findById(transferId)).thenReturn(Optional.of(sampleOrder(ownerId)));
    GatewayUser stranger = new GatewayUser(otherId, List.of("CUSTOMER"), List.of());
    BusinessException ex = assertThrows(BusinessException.class, () -> service.getDetail(transferId, stranger));
    assertEquals("FORBIDDEN", ex.getCode());
  }

  private TransferOrderEntity sampleOrder(UUID userId) {
    TransferOrderEntity e = new TransferOrderEntity();
    e.setId(transferId);
    e.setUserId(userId);
    e.setFromAccountId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    e.setToAccountId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
    e.setToAccountNumber("1000000001");
    e.setAmount(new BigDecimal("10000"));
    e.setFeeAmount(BigDecimal.ZERO);
    e.setCurrency("VND");
    e.setDescription("test");
    e.setStatus(TransferStatus.COMPLETED);
    e.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
    e.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
    return e;
  }
}
