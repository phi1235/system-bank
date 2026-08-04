package com.banksystem.transaction.application.mapper;

import com.banksystem.transaction.api.dto.TransferDtos.SagaStepResponse;
import com.banksystem.transaction.api.dto.TransferDtos.TransferResponse;
import com.banksystem.transaction.domain.SagaStepLogEntity;
import com.banksystem.transaction.domain.TransferOrderEntity;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class TransferMapper {

  public TransferResponse toResponse(TransferOrderEntity e) {
    if (e == null) return null;
    BigDecimal fee = e.getFeeAmount() == null ? BigDecimal.ZERO : e.getFeeAmount();
    return new TransferResponse(
        e.getId().toString(),
        e.getStatus().name(),
        e.getFromAccountId().toString(),
        e.getToAccountId() == null ? null : e.getToAccountId().toString(),
        e.getToAccountNumber(),
        e.getAmount(),
        fee,
        e.getCurrency(),
        e.getDescription(),
        e.getFailureReason(),
        e.getCreatedAt(),
        "INTERNAL",
        "SYSTEM_BANK",
        null
    );
  }

  public SagaStepResponse toStep(SagaStepLogEntity s) {
    if (s == null) return null;
    return new SagaStepResponse(
        s.getId().toString(),
        s.getStep(),
        s.getStatus(),
        s.getDetail(),
        s.getCreatedAt()
    );
  }
}
