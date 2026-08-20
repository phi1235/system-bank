package com.banksystem.transaction.application.collection;

import com.banksystem.transaction.domain.collection.InboundPaymentStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record InboundEventClaimContext(
    UUID eventId,
    String provider,
    String providerTransactionId,
    String virtualAccountNumber,
    String bankBin,
    BigDecimal amount,
    String currency,
    InboundPaymentStatus status,
    UUID ledgerJournalId,
    int retryCount,
    UUID claimToken
) {}
