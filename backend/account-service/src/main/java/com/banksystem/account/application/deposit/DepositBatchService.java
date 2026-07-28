package com.banksystem.account.application.deposit;

import com.banksystem.account.api.dto.deposit.DepositDtos.BatchRunResponse;
import com.banksystem.account.domain.entity.deposit.TermDepositEntity;
import com.banksystem.account.domain.enums.deposit.TermDepositStatus;
import com.banksystem.account.domain.repository.deposit.TermDepositRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * End-of-day deposit batch: (1) set-based interest accrual for the whole OPEN book, then
 * (2) per-deposit maturity settlement. Maturities run one transaction each (via
 * {@link TermDepositService#mature}) so a single failure is logged and skipped, never
 * aborting the rest of the batch.
 */
@Service
public class DepositBatchService {

  private static final Logger log = LoggerFactory.getLogger(DepositBatchService.class);

  private final TermDepositRepository depositRepository;
  private final TermDepositService termDepositService;
  private final TransactionTemplate transactionTemplate;
  private final Clock clock;
  private final ZoneId zone;

  public DepositBatchService(
      TermDepositRepository depositRepository,
      TermDepositService termDepositService,
      TransactionTemplate transactionTemplate,
      Clock clock,
      @Value("${bank.deposit.zone}") String zone) {
    this.depositRepository = depositRepository;
    this.termDepositService = termDepositService;
    this.transactionTemplate = transactionTemplate;
    this.clock = clock;
    this.zone = ZoneId.of(zone);
  }

  public BatchRunResponse run() {
    Integer accrued =
        transactionTemplate.execute(s -> depositRepository.accrueDailyInterest(zone.getId()));

    LocalDate today = LocalDate.now(clock.withZone(zone));
    List<UUID> due =
        depositRepository
            .findByStatusAndMaturityDateLessThanEqual(TermDepositStatus.OPEN, today)
            .stream()
            .map(TermDepositEntity::getId)
            .toList();

    int matured = 0;
    int failed = 0;
    for (UUID id : due) {
      try {
        if (termDepositService.mature(id)) {
          matured++;
        }
      } catch (Exception ex) {
        failed++;
        log.error("Deposit maturity failed id={}: {}", id, ex.getMessage(), ex);
      }
    }
    BatchRunResponse result =
        new BatchRunResponse(accrued == null ? 0 : accrued, matured, failed);
    log.info(
        "Deposit batch done: accrued={} due={} matured={} failed={}",
        result.accruedUpdated(), due.size(), result.matured(), result.failed());
    return result;
  }
}
