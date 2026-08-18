package com.banksystem.transaction.infrastructure.gateway;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.transaction.application.forensics.LedgerEvidenceGateway;
import com.banksystem.transaction.infrastructure.feign.AccountClient;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.AccountHoldView;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.FinancialEventView;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.LedgerJournalView;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.LedgerPostingView;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.TransactionLedgerEvidenceView;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.AccountStateEvidenceView;
import com.banksystem.transaction.application.forensics.LedgerEvidenceGateway.AccountStateEvidence;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FeignLedgerEvidenceGateway implements LedgerEvidenceGateway {
  private final AccountClient accountClient;
  private final String internalApiKey;

  public FeignLedgerEvidenceGateway(
      Optional<AccountClient> accountClient,
      @Value("${bank.internal.account-api-key}") String internalApiKey) {
    this.accountClient = accountClient.orElse(null);
    this.internalApiKey = internalApiKey;
  }

  @Override
  public Optional<TransactionLedgerEvidence> findByTransactionId(UUID transactionId) {
    if (accountClient == null) {
      return Optional.empty();
    }
    try {
      ApiResponse<TransactionLedgerEvidenceView> response =
          accountClient.transactionLedgerEvidence(transactionId, internalApiKey);
      return response == null || response.data() == null
          ? Optional.empty()
          : Optional.of(map(response.data()));
    } catch (BusinessException exception) {
      if ("ACCOUNT_SERVICE_UNAVAILABLE".equals(exception.getCode())) {
        return Optional.empty();
      }
      throw exception;
    }
  }

  @Override
  public Optional<AccountStateEvidence> findAccountState(UUID accountId, Instant at) {
    if (accountClient == null) return Optional.empty();
    try {
      ApiResponse<AccountStateEvidenceView> response =
          accountClient.accountStateEvidence(accountId, at, internalApiKey);
      if (response == null || response.data() == null) return Optional.empty();
      AccountStateEvidenceView source = response.data();
      return Optional.of(new AccountStateEvidence(
          source.accountId(), source.currency(), source.ledgerBalance(), source.activeHoldAmount(),
          source.availableBalance(), source.at(), source.completeness()));
    } catch (BusinessException exception) {
      if ("ACCOUNT_SERVICE_UNAVAILABLE".equals(exception.getCode())) return Optional.empty();
      throw exception;
    }
  }

  private TransactionLedgerEvidence map(TransactionLedgerEvidenceView source) {
    return new TransactionLedgerEvidence(
        source.journals().stream().map(this::mapJournal).toList(),
        source.holds().stream().map(this::mapHold).toList(),
        source.events().stream().map(this::mapEvent).toList(),
        source.completeness());
  }

  private JournalEvidence mapJournal(LedgerJournalView source) {
    return new JournalEvidence(
        source.id(), source.businessCommandId(), source.businessReference(), source.journalType(),
        source.status(), source.currency(), source.description(), source.reversalOfJournalId(),
        source.sequenceNo(), source.createdAt(), source.postedAt(),
        source.postings().stream().map(this::mapPosting).toList());
  }

  private PostingEvidence mapPosting(LedgerPostingView source) {
    return new PostingEvidence(
        source.id(), source.accountId(), source.ledgerAccountCode(), source.side(), source.amount(),
        source.currency(), source.createdAt());
  }

  private HoldEvidence mapHold(AccountHoldView source) {
    return new HoldEvidence(
        source.id(), source.accountId(), source.amount(), source.currency(), source.status(),
        source.expiresAt(), source.capturedJournalId(), source.createdAt(), source.updatedAt());
  }

  private FinancialEventEvidence mapEvent(FinancialEventView source) {
    return new FinancialEventEvidence(
        source.eventId(), source.aggregateType(), source.aggregateId(), source.sequenceNo(),
        source.eventType(), source.schemaVersion(), source.occurredAt(), source.payload(),
        source.payloadSha256());
  }
}
