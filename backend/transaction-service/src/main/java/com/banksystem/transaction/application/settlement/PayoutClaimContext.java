package com.banksystem.transaction.application.settlement;

import com.banksystem.transaction.domain.settlement.B2bPayoutStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record PayoutClaimContext(
    UUID payoutId,
    String clientRequestId,
    UUID organizationId,
    UUID settlementLegId,
    String payoutType,
    BigDecimal amount,
    String currency,
    UUID beneficiaryAccountId,
    String beneficiaryBankBin,
    String beneficiaryAccountNumber,
    String beneficiaryName,
    B2bPayoutStatus status,
    int retryCount,
    UUID claimToken
) {}
