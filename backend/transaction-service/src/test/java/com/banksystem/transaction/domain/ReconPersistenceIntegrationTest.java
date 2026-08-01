package com.banksystem.transaction.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Persistence round-trip on real Postgres (Flyway-migrated Testcontainer). Context startup itself
 * is an assertion: {@code ddl-auto: validate} checks every entity — including the V9 recon
 * tables — against the migrated schema. Skipped when Docker is off.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ReconPersistenceIntegrationTest {

  @Container
  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired private ReconRunRepository runRepository;
  @Autowired private ReconItemRepository itemRepository;
  @Autowired private TransferOrderRepository transferOrderRepository;

  @Test
  void reconRunAndItemsRoundTripWithOrdering() {
    ReconRunEntity older = run(LocalDate.of(2026, 7, 24), Instant.parse("2026-07-25T00:30:00Z"));
    ReconRunEntity newer = run(LocalDate.of(2026, 7, 25), Instant.parse("2026-07-26T00:30:00Z"));
    runRepository.saveAll(List.of(older, newer));

    itemRepository.saveAll(
        List.of(
            item(newer.getId(), "MISSING_REFUND"),
            item(newer.getId(), "AMOUNT_MISMATCH_DEBIT")));

    List<ReconRunEntity> page =
        runRepository
            .findAllByOrderByStartedAtDesc(PageRequest.of(0, 10))
            .getContent();
    assertEquals(newer.getId(), page.get(0).getId());
    assertEquals(older.getId(), page.get(1).getId());

    List<ReconItemEntity> items =
        itemRepository.findByRunIdOrderByKindAscTransferIdAsc(newer.getId());
    assertEquals(2, items.size());
    assertEquals("AMOUNT_MISMATCH_DEBIT", items.get(0).getKind());
    assertEquals("MISSING_REFUND", items.get(1).getKind());
  }

  @Test
  void transferRangeQueryIsHalfOpen() {
    Instant from = Instant.parse("2026-07-24T17:00:00Z");
    Instant to = Instant.parse("2026-07-25T17:00:00Z");
    transferOrderRepository.saveAll(
        List.of(
            transfer(from),                       // inclusive lower bound — in
            transfer(from.plusSeconds(3600)),     // middle — in
            transfer(to)));                       // exclusive upper bound — out

    assertEquals(
        2,
        transferOrderRepository
            .findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, to)
            .size());
  }

  private static ReconRunEntity run(LocalDate date, Instant startedAt) {
    ReconRunEntity r = new ReconRunEntity();
    r.setId(UUID.randomUUID());
    r.setBusinessDate(date);
    r.setZone("Asia/Bangkok");
    r.setTriggerType(ReconRunEntity.TRIGGER_MANUAL);
    r.setStatus(ReconRunEntity.STATUS_MISMATCHED);
    r.setStartedAt(startedAt);
    r.setFinishedAt(startedAt.plusSeconds(2));
    r.setOrdersChecked(3);
    r.setLedgerEntriesSeen(5);
    r.setDiscrepancyCount(2);
    return r;
  }

  private static ReconItemEntity item(UUID runId, String kind) {
    ReconItemEntity i = new ReconItemEntity();
    i.setId(UUID.randomUUID());
    i.setRunId(runId);
    i.setTransferId(UUID.randomUUID());
    i.setKind(kind);
    i.setEntryRef("ref-" + kind);
    i.setExpectedAmount(new BigDecimal("100.00"));
    i.setActualAmount(new BigDecimal("90.00"));
    i.setDetail("integration test");
    return i;
  }

  private static TransferOrderEntity transfer(Instant createdAt) {
    TransferOrderEntity t = new TransferOrderEntity();
    t.setId(UUID.randomUUID());
    t.setIdempotencyKey("idem-" + t.getId());
    t.setUserId(UUID.randomUUID());
    t.setFromAccountId(UUID.randomUUID());
    t.setToAccountNumber("0000000001");
    t.setAmount(new BigDecimal("100.00"));
    t.setRequestFingerprint("fp-" + t.getId());
    t.setStatus(TransferStatus.COMPLETED);
    t.setCreatedAt(createdAt);
    t.setUpdatedAt(createdAt);
    return t;
  }
}
