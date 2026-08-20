package com.banksystem.transaction.application.reconciliation;

import com.banksystem.common.api.PageResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.api.dto.ReconDtos.ReconItemResponse;
import com.banksystem.transaction.api.dto.ReconDtos.ReconRunDetailResponse;
import com.banksystem.transaction.api.dto.ReconDtos.ReconRunResponse;
import com.banksystem.transaction.application.reconciliation.ReconciliationMatcher.Discrepancy;
import com.banksystem.transaction.application.gateway.LedgerGateway;
import com.banksystem.transaction.domain.reconciliation.ReconItemEntity;
import com.banksystem.transaction.domain.reconciliation.ReconItemRepository;
import com.banksystem.transaction.domain.reconciliation.ReconRunEntity;
import com.banksystem.transaction.domain.reconciliation.ReconRunRepository;
import com.banksystem.transaction.domain.transfer.TransferOrderEntity;
import com.banksystem.transaction.domain.transfer.TransferOrderRepository;
import com.banksystem.transaction.infrastructure.feign.LedgerClientDtos.LedgerEntryView;
import com.banksystem.transaction.infrastructure.feign.LedgerClientDtos.LedgerSearchRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * End-of-day reconciliation orchestration: load the banking day's transfer orders, pull the
 * matching ledger entries from account-service via LedgerGateway,
 * run {@link ReconciliationMatcher}, persist the run and its discrepancies.
 */
@Service
public class ReconciliationService {

  private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);
  /** References per internal search call; 4 refs per order → ~125 orders per call. */
  static final int REF_CHUNK_SIZE = 500;

  private final TransferOrderRepository transferOrderRepository;
  private final ReconRunRepository runRepository;
  private final ReconItemRepository itemRepository;
  private final LedgerGateway ledgerGateway;
  private final ReconciliationMatcher matcher;
  private final Clock clock;
  private final ZoneId zone;

  public ReconciliationService(
      TransferOrderRepository transferOrderRepository,
      ReconRunRepository runRepository,
      ReconItemRepository itemRepository,
      LedgerGateway ledgerGateway,
      ReconciliationMatcher matcher,
      Clock clock,
      @Value("${bank.transfer.daily-limit-zone}") String zone) {
    this.transferOrderRepository = transferOrderRepository;
    this.runRepository = runRepository;
    this.itemRepository = itemRepository;
    this.ledgerGateway = ledgerGateway;
    this.matcher = matcher;
    this.clock = clock;
    this.zone = ZoneId.of(zone);
  }

  public ReconRunResponse runForDate(LocalDate date, String triggerType) {
    if (date == null) {
      throw new BusinessException("RECON_DATE_REQUIRED", "date is required");
    }
    if (date.isAfter(LocalDate.now(clock.withZone(zone)))) {
      throw new BusinessException(
          "RECON_DATE_IN_FUTURE", "Cannot reconcile a future date");
    }

    ReconRunEntity run = new ReconRunEntity();
    run.setId(UUID.randomUUID());
    run.setBusinessDate(date);
    run.setZone(zone.getId());
    run.setTriggerType(triggerType);
    run.setStatus(ReconRunEntity.STATUS_RUNNING);
    run.setStartedAt(clock.instant());
    runRepository.save(run);
    log.info("[RECON-RUN] Starting recon run [{}] Date=[{}] Trigger=[{}]", run.getId(), date, triggerType);

    try {
      Instant fromTs = date.atStartOfDay(zone).toInstant();
      Instant toTs = date.plusDays(1).atStartOfDay(zone).toInstant();
      List<TransferOrderEntity> transfers =
          transferOrderRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
              fromTs, toTs);
      List<LedgerEntryView> entries = fetchLedgerEntries(transfers);
      List<Discrepancy> discrepancies = matcher.match(transfers, entries);

      itemRepository.saveAll(discrepancies.stream().map(d -> toItem(run.getId(), d)).toList());
      run.setOrdersChecked(transfers.size());
      run.setLedgerEntriesSeen(entries.size());
      run.setDiscrepancyCount(discrepancies.size());
      run.setStatus(
          discrepancies.isEmpty()
              ? ReconRunEntity.STATUS_MATCHED
              : ReconRunEntity.STATUS_MISMATCHED);
      run.setFinishedAt(clock.instant());
      runRepository.save(run);
      log.info(
          "[RECON-FINISHED] Recon [{}] Status=[{}] Date=[{}] OrdersChecked=[{}] LedgerSeen=[{}] Discrepancies=[{}]",
          run.getId(), run.getStatus(), date, transfers.size(), entries.size(), discrepancies.size());
    } catch (Exception ex) {
      log.error("[RECON-FAILED] Recon [{}] FAILED Date=[{}]: {}", run.getId(), date, ex.getMessage(), ex);
      run.setStatus(ReconRunEntity.STATUS_FAILED);
      run.setErrorDetail(truncate(ex.getMessage(), 500));
      run.setFinishedAt(clock.instant());
      runRepository.save(run);
    }
    return toResponse(run);
  }

  public ReconRunResponse runManual(LocalDate date) {
    return runForDate(date, ReconRunEntity.TRIGGER_MANUAL);
  }

  public PageResponse<ReconRunResponse> list(Integer page, Integer size) {
    int p = page == null || page < 0 ? 0 : page;
    int s = size == null || size < 1 ? 20 : Math.min(size, 100);
    Page<ReconRunEntity> result = runRepository.findAllByOrderByStartedAtDesc(PageRequest.of(p, s));
    return new PageResponse<>(
        result.getContent().stream().map(this::toResponse).toList(),
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages());
  }

  public ReconRunDetailResponse get(UUID id) {
    ReconRunEntity run =
        runRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new BusinessException(
                        "RECON_RUN_NOT_FOUND", "Recon run not found"));
    List<ReconItemResponse> items =
        itemRepository.findByRunIdOrderByKindAscTransferIdAsc(id).stream()
            .map(this::toItemResponse)
            .toList();
    return new ReconRunDetailResponse(toResponse(run), items);
  }

  private List<LedgerEntryView> fetchLedgerEntries(List<TransferOrderEntity> transfers) {
    if (transfers.isEmpty()) {
      return List.of();
    }
    List<String> refs = new ArrayList<>(transfers.size() * 4);
    for (TransferOrderEntity t : transfers) {
      String id = t.getId().toString();
      refs.add(id);
      refs.add(id + ReconciliationMatcher.SUFFIX_FEE);
      refs.add(id + ReconciliationMatcher.SUFFIX_COMPENSATION);
      refs.add(id + ReconciliationMatcher.SUFFIX_REVERSE_DEST);
    }
    List<LedgerEntryView> out = new ArrayList<>();
    for (int i = 0; i < refs.size(); i += REF_CHUNK_SIZE) {
      List<String> chunk = refs.subList(i, Math.min(i + REF_CHUNK_SIZE, refs.size()));
      List<LedgerEntryView> res = ledgerGateway.searchLedger(new LedgerSearchRequest(chunk));
      if (res == null) {
        throw new BusinessException(
            "RECON_LEDGER_FETCH_FAILED",
            "Ledger lookup failed for chunk starting at " + i);
      }
      out.addAll(res);
    }
    return out;
  }

  private ReconItemEntity toItem(UUID runId, Discrepancy d) {
    ReconItemEntity e = new ReconItemEntity();
    e.setId(UUID.randomUUID());
    e.setRunId(runId);
    e.setTransferId(d.transferId());
    e.setKind(d.kind());
    e.setEntryRef(d.entryRef());
    e.setExpectedAmount(d.expectedAmount());
    e.setActualAmount(d.actualAmount());
    e.setDetail(d.detail());
    return e;
  }

  private ReconRunResponse toResponse(ReconRunEntity e) {
    return new ReconRunResponse(
        e.getId().toString(),
        e.getBusinessDate(),
        e.getZone(),
        e.getTriggerType(),
        e.getStatus(),
        e.getStartedAt(),
        e.getFinishedAt(),
        e.getOrdersChecked(),
        e.getLedgerEntriesSeen(),
        e.getDiscrepancyCount(),
        e.getErrorDetail());
  }

  private ReconItemResponse toItemResponse(ReconItemEntity e) {
    return new ReconItemResponse(
        e.getId().toString(),
        e.getTransferId() == null ? null : e.getTransferId().toString(),
        e.getKind(),
        e.getEntryRef(),
        e.getExpectedAmount(),
        e.getActualAmount(),
        e.getDetail());
  }

  private static String truncate(String text, int max) {
    if (text == null) {
      return null;
    }
    return text.length() <= max ? text : text.substring(0, max);
  }
}
