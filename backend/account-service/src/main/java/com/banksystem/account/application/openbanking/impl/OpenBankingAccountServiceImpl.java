package com.banksystem.account.application.openbanking.impl;

import com.banksystem.account.api.dto.AccountDtos.AccountResponse;
import com.banksystem.account.api.dto.AccountDtos.StatementFilterRequest;
import com.banksystem.account.application.account.AccountMapper;
import com.banksystem.account.application.ledger.LedgerStatementQuery;
import com.banksystem.account.application.openbanking.OpenBankingAccountService;
import com.banksystem.account.domain.account.AccountEntity;
import com.banksystem.account.domain.account.AccountRepository;
import com.banksystem.account.domain.ledger.LedgerEntryEntity;
import com.banksystem.account.domain.ledger.LedgerEntryRepository;
import com.banksystem.common.exception.BusinessException;
import com.banksystem.common.iso20022.Camt053Dto;
import com.banksystem.common.iso20022.Camt053Dto.Account;
import com.banksystem.common.iso20022.Camt053Dto.Amount;
import com.banksystem.common.iso20022.Camt053Dto.Balance;
import com.banksystem.common.iso20022.Camt053Dto.Entry;
import com.banksystem.common.iso20022.Camt053Dto.EntryDetails;
import com.banksystem.common.iso20022.Camt053Dto.GroupHeader;
import com.banksystem.common.iso20022.Camt053Dto.PartyIdentification;
import com.banksystem.common.iso20022.Camt053Dto.RemittanceInformation;
import com.banksystem.common.iso20022.Camt053Dto.Statement;
import com.banksystem.common.iso20022.Camt053Dto.TransactionDetails;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpenBankingAccountServiceImpl implements OpenBankingAccountService {

  private final AccountRepository accountRepository;
  private final LedgerEntryRepository ledgerEntryRepository;
  private final AccountMapper accountMapper;

  public OpenBankingAccountServiceImpl(
      AccountRepository accountRepository,
      LedgerEntryRepository ledgerEntryRepository,
      AccountMapper accountMapper) {
    this.accountRepository = accountRepository;
    this.ledgerEntryRepository = ledgerEntryRepository;
    this.accountMapper = accountMapper;
  }

  @Override
  @Transactional(readOnly = true)
  public List<AccountResponse> listAccountsForB2bClient(String clientId) {
    List<AccountEntity> accounts = accountRepository.findAll();
    return accounts.stream()
        .map(accountMapper::toResponse)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public AccountResponse getAccountBalanceForB2bClient(String clientId, String accountNumber) {
    AccountEntity account = accountRepository.findByAccountNumber(accountNumber)
        .orElseThrow(() -> new BusinessException("ACCOUNT_NOT_FOUND", "Account not found: " + accountNumber, HttpStatus.NOT_FOUND));
    return accountMapper.toResponse(account);
  }

  @Override
  @Transactional(readOnly = true)
  public Camt053Dto generateCamt053Statement(String clientId, String accountNumber, StatementFilterRequest filter) {
    AccountEntity account = accountRepository.findByAccountNumber(accountNumber)
        .orElseThrow(() -> new BusinessException("ACCOUNT_NOT_FOUND", "Account not found: " + accountNumber, HttpStatus.NOT_FOUND));

    Instant fromTs = (filter != null && filter.from() != null) ? filter.from() : LedgerStatementQuery.EPOCH;
    Instant toTs = (filter != null && filter.to() != null) ? filter.to() : LedgerStatementQuery.FAR_FUTURE;
    int page = (filter != null && filter.page() != null && filter.page() >= 0) ? filter.page() : 0;
    int size = (filter != null && filter.size() != null && filter.size() >= 1) ? Math.min(filter.size(), 100) : 50;

    Page<LedgerEntryEntity> entryPage = ledgerEntryRepository.search(
        account.getId(),
        false,
        "",
        fromTs,
        toTs,
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
    );

    BigDecimal openingBalanceVal = ledgerEntryRepository.balanceAt(account.getId(), fromTs);
    BigDecimal closingBalanceVal = ledgerEntryRepository.balanceAt(account.getId(), toTs.isAfter(Instant.now()) ? Instant.now() : toTs);

    String nowIso = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
    String fromIso = DateTimeFormatter.ISO_INSTANT.format(fromTs);
    String toIso = DateTimeFormatter.ISO_INSTANT.format(toTs.isAfter(Instant.now()) ? Instant.now() : toTs);

    GroupHeader grpHdr = new GroupHeader("STMT-MSG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(), nowIso);

    List<Balance> balances = List.of(
        new Balance("OPBD", new Amount(account.getCurrency(), openingBalanceVal != null ? openingBalanceVal : BigDecimal.ZERO), "CRDT", fromIso),
        new Balance("CLBD", new Amount(account.getCurrency(), closingBalanceVal != null ? closingBalanceVal : account.getBalance()), "CRDT", toIso)
    );

    List<Entry> entries = new ArrayList<>();
    for (LedgerEntryEntity e : entryPage.getContent()) {
      boolean isCredit = "CREDIT".equalsIgnoreCase(e.getEntryType());
      String crDb = isCredit ? "CRDT" : "DBIT";
      String bookingDate = DateTimeFormatter.ISO_INSTANT.format(e.getCreatedAt());

      TransactionDetails txDtls = new TransactionDetails(
          e.getReferenceId() != null ? e.getReferenceId() : "TX-" + e.getId().toString().substring(0, 8),
          new PartyIdentification(isCredit ? "BENEFICIARY" : "COUNTERPARTY"),
          new PartyIdentification(account.getAccountNumber()),
          new RemittanceInformation(e.getDescription() != null ? e.getDescription() : "Ledger transfer")
      );

      entries.add(new Entry(
          "LEDGER-TX-" + e.getId(),
          new Amount(account.getCurrency(), e.getAmount()),
          crDb,
          "BOOK",
          bookingDate,
          "PMNT-ICDT-ESCT",
          new EntryDetails(txDtls)
      ));
    }

    Statement stmt = new Statement(
        "STMT-" + account.getAccountNumber() + "-" + System.currentTimeMillis(),
        1L,
        new Account(account.getAccountNumber(), account.getCurrency(), "ENTERPRISE CLIENT"),
        balances,
        entries
    );

    return new Camt053Dto(grpHdr, List.of(stmt));
  }
}
