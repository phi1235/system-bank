package com.banksystem.transaction.application.metrics;

import com.banksystem.transaction.api.dto.TransferDtos.InternalTransactionCountsResponse;
import com.banksystem.transaction.domain.outbox.OutboxEventRepository;
import com.banksystem.transaction.domain.outbox.OutboxStatus;
import com.banksystem.transaction.domain.transfer.TransferOrderRepository;
import com.banksystem.transaction.domain.transfer.TransferStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InternalTransactionQueryService {

  private final TransferOrderRepository transferRepository;
  private final OutboxEventRepository outboxRepository;
  private final OperationalRowCountPort rowCountPort;

  public InternalTransactionQueryService(
      TransferOrderRepository transferRepository,
      OutboxEventRepository outboxRepository,
      OperationalRowCountPort rowCountPort) {
    this.transferRepository = transferRepository;
    this.outboxRepository = outboxRepository;
    this.rowCountPort = rowCountPort;
  }

  @Transactional(readOnly = true)
  public InternalTransactionCountsResponse counts() {
    return new InternalTransactionCountsResponse(
        rowCountPort.transferOrders(),
        transferRepository.countByStatus(TransferStatus.FAILED),
        transferRepository.countByStatus(TransferStatus.COMPENSATED),
        rowCountPort.auditLogs(),
        outboxRepository.countByStatus(OutboxStatus.DEAD.name()),
        outboxRepository.countByStatus(OutboxStatus.PENDING.name()),
        outboxRepository.countByStatus(OutboxStatus.PUBLISHED.name()));
  }
}
