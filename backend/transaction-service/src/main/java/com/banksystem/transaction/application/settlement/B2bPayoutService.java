package com.banksystem.transaction.application.settlement;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.domain.settlement.B2bPayoutEntity;
import com.banksystem.transaction.domain.settlement.B2bPayoutRepository;
import com.banksystem.transaction.domain.settlement.B2bPayoutStatus;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class B2bPayoutService {

  private static final Logger log = LoggerFactory.getLogger(B2bPayoutService.class);

  private final B2bPayoutRepository payoutRepository;
  private final NapasPayoutExecutor napasPayoutExecutor;

  public B2bPayoutService(
      B2bPayoutRepository payoutRepository,
      NapasPayoutExecutor napasPayoutExecutor) {
    this.payoutRepository = payoutRepository;
    this.napasPayoutExecutor = napasPayoutExecutor;
  }

  public B2bPayoutEntity getById(UUID payoutId) {
    return payoutRepository.findById(payoutId)
        .orElseThrow(() -> new BusinessException("PAYOUT_NOT_FOUND", "Payout not found: " + payoutId));
  }

  public List<B2bPayoutEntity> getByOrganizationId(UUID organizationId) {
    return payoutRepository.findByOrganizationId(organizationId);
  }

  public Page<B2bPayoutEntity> search(UUID organizationId, B2bPayoutStatus status, Pageable pageable) {
    return payoutRepository.search(organizationId, status, pageable);
  }
}
