package com.banksystem.transaction.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banksystem.common.api.PageResponse;
import com.banksystem.transaction.api.dto.TransferDtos.TransferResponse;
import com.banksystem.transaction.application.mapper.TransferMapper;
import com.banksystem.transaction.application.query.AdminTransferListQuery;
import com.banksystem.transaction.domain.SagaStepLogRepository;
import com.banksystem.transaction.domain.TransferOrderEntity;
import com.banksystem.transaction.domain.TransferOrderRepository;
import com.banksystem.transaction.domain.TransferStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class TransferServiceAdminListTest {

  private TransferOrderRepository repository;
  private TransferQueryService queryService;

  @BeforeEach
  void setUp() {
    repository = mock(TransferOrderRepository.class);
    queryService =
        new TransferQueryService(
            repository,
            mock(SagaStepLogRepository.class),
            mock(TransferLimitPolicy.class),
            mock(TransferFeePolicy.class),
            new TransferMapper());
  }

  @Test
  void adminList_passesFlagsAndMapsPage() {
    TransferOrderEntity row = sample();
    when(repository.adminSearch(
            anyBoolean(),
            any(),
            anyBoolean(),
            any(),
            anyBoolean(),
            any(),
            any(),
            any(),
            any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));

    PageResponse<TransferResponse> page =
        queryService.adminList(AdminTransferListQuery.of("FAILED", null, "9988", null, null, 0, 20));

    assertEquals(1, page.items().size());
    assertEquals(row.getId().toString(), page.items().get(0).transactionId());
    assertEquals("FAILED", page.items().get(0).status());
    verify(repository)
        .adminSearch(
            anyBoolean(),
            any(),
            anyBoolean(),
            any(),
            anyBoolean(),
            any(),
            any(),
            any(),
            any(Pageable.class));
  }

  private TransferOrderEntity sample() {
    TransferOrderEntity e = new TransferOrderEntity();
    e.setId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    e.setUserId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
    e.setFromAccountId(UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"));
    e.setToAccountNumber("9988776655");
    e.setAmount(new BigDecimal("100000"));
    e.setFeeAmount(BigDecimal.ZERO);
    e.setCurrency("VND");
    e.setStatus(TransferStatus.FAILED);
    e.setCreatedAt(Instant.parse("2026-07-01T00:00:00Z"));
    e.setUpdatedAt(Instant.parse("2026-07-01T00:00:00Z"));
    e.setIdempotencyKey("idem-1");
    e.setRequestFingerprint("fp-1");
    return e;
  }
}
