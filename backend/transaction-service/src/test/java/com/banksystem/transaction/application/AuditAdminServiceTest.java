package com.banksystem.transaction.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banksystem.common.api.PageResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.api.dto.TransferDtos.AuditResponse;
import com.banksystem.transaction.application.query.AuditListQuery;
import com.banksystem.transaction.domain.AuditLogEntity;
import com.banksystem.transaction.domain.AuditLogRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class AuditAdminServiceTest {

  private AuditLogRepository repository;
  private AuditAdminService service;

  @BeforeEach
  void setUp() {
    repository = mock(AuditLogRepository.class);
    service = new AuditAdminService(repository);
  }

  @Test
  void list_mapsPageAndPassesFlags() {
    AuditLogEntity row = sample();
    when(repository.searchAdmin(
            eq(true),
            eq("TRANSFER_CREATE"),
            eq(false),
            eq(""),
            eq(false),
            any(UUID.class),
            eq(false),
            eq(""),
            eq(AuditListQuery.EPOCH),
            eq(AuditListQuery.FAR_FUTURE),
            any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));

    PageResponse<AuditResponse> page =
        service.list(AuditListQuery.of("TRANSFER_CREATE", null, null, null, null, null, 0, 20));

    assertEquals(1, page.items().size());
    assertEquals(row.getId().toString(), page.items().get(0).id());
    assertEquals("TRANSFER_CREATE", page.items().get(0).action());
    assertEquals("amount=1000", page.items().get(0).metadata());
  }

  @Test
  void get_returnsDetail() {
    AuditLogEntity row = sample();
    when(repository.findById(row.getId())).thenReturn(Optional.of(row));

    AuditResponse res = service.get(row.getId());

    assertEquals(row.getId().toString(), res.id());
    assertEquals("TRANSFER", res.resourceType());
    verify(repository).findById(row.getId());
  }

  @Test
  void get_notFound() {
    UUID id = UUID.randomUUID();
    when(repository.findById(id)).thenReturn(Optional.empty());
    BusinessException ex = assertThrows(BusinessException.class, () -> service.get(id));
    assertEquals("AUDIT_NOT_FOUND", ex.getCode());
  }

  private AuditLogEntity sample() {
    return AuditLogEntity.of(
        UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
        "TRANSFER_CREATE",
        "TRANSFER",
        "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
        "127.0.0.1",
        "amount=1000");
  }
}
