package com.banksystem.account.application.sweep;

import com.banksystem.account.application.ledger.DoubleEntryJournalService;
import com.banksystem.account.domain.account.AccountEntity;
import com.banksystem.account.domain.account.AccountRepository;
import com.banksystem.account.domain.ledger.LedgerEntryEntity;
import com.banksystem.account.domain.ledger.LedgerEntryRepository;
import com.banksystem.account.domain.sweep.AutoSweepOperationEntity;
import com.banksystem.account.domain.sweep.AutoSweepOperationRepository;
import com.banksystem.account.domain.sweep.AutoSweepPositionEntity;
import com.banksystem.account.domain.sweep.AutoSweepPositionRepository;
import com.banksystem.account.domain.sweep.AutoSweepProfileEntity;
import com.banksystem.account.domain.sweep.AutoSweepProfileRepository;
import com.banksystem.account.domain.sweep.SweepProductEntity;
import com.banksystem.account.domain.sweep.SweepProductRepository;
import com.banksystem.account.infrastructure.sweep.AutoSweepBalanceRepository;
import com.banksystem.account.infrastructure.sweep.AutoSweepBalanceRepository.BalanceState;
import com.banksystem.common.exception.BusinessException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Atomic CASA/flexible-position movements. Account is always locked before profile/position. */
@Service
public class AutoSweepMovementService {
  private final AccountRepository accountRepository;
  private final AutoSweepProfileRepository profileRepository;
  private final AutoSweepPositionRepository positionRepository;
  private final AutoSweepOperationRepository operationRepository;
  private final SweepProductRepository productRepository;
  private final LedgerEntryRepository ledgerEntryRepository;
  private final DoubleEntryJournalService journalService;
  private final AutoSweepBalanceRepository balanceRepository;
  private final Clock clock;
  private final ZoneId zone;

  public AutoSweepMovementService(
      AccountRepository accountRepository,
      AutoSweepProfileRepository profileRepository,
      AutoSweepPositionRepository positionRepository,
      AutoSweepOperationRepository operationRepository,
      SweepProductRepository productRepository,
      LedgerEntryRepository ledgerEntryRepository,
      DoubleEntryJournalService journalService,
      AutoSweepBalanceRepository balanceRepository,
      Clock clock,
      @Value("${bank.deposit.zone}") String zone) {
    this.accountRepository = accountRepository;
    this.profileRepository = profileRepository;
    this.positionRepository = positionRepository;
    this.operationRepository = operationRepository;
    this.productRepository = productRepository;
    this.ledgerEntryRepository = ledgerEntryRepository;
    this.journalService = journalService;
    this.balanceRepository = balanceRepository;
    this.clock = clock;
    this.zone = ZoneId.of(zone);
  }

  /** Adds exactly the payment deficit to CASA when the configured flexible position can cover it. */
  @Transactional
  public BigDecimal ensurePaymentLiquidity(
      AccountEntity lockedAccount,
      BigDecimal requiredAmount,
      String paymentCommandId,
      String paymentReference) {
    BalanceState balance = balance(lockedAccount.getId());
    if (balance.available().compareTo(requiredAmount) >= 0) return BigDecimal.ZERO;
    if (!"PAYMENT".equalsIgnoreCase(lockedAccount.getAccountType())) return BigDecimal.ZERO;

    AutoSweepProfileEntity profile = profileRepository
        .findBySourceAccountIdForUpdate(lockedAccount.getId()).orElse(null);
    if (profile == null || !"ENABLED".equals(profile.getStatus())) return BigDecimal.ZERO;

    BigDecimal deficit = requiredAmount.subtract(balance.available());
    AutoSweepPositionEntity position = positionRepository
        .findByProfileIdForUpdate(profile.getId())
        .orElseThrow(() -> new BusinessException(
            "AUTO_SWEEP_POSITION_NOT_FOUND", "Sweep position not found"));
    SweepProductEntity product = productRepository.findById(profile.getProductCode())
        .orElseThrow(() -> new BusinessException(
            "SWEEP_PRODUCT_NOT_FOUND", "Sweep product not found"));
    LocalDate businessDate = LocalDate.now(clock.withZone(zone));
    accrue(profile, position, product, lockedAccount, businessDate, "PAYMENT");
    if (position.getPrincipalBalance().compareTo(deficit) < 0) return BigDecimal.ZERO;

    String operationCommand = paymentOperationCommand(
        lockedAccount.getId(), paymentCommandId, requiredAmount);
    AutoSweepOperationEntity duplicate = operationRepository.findByCommandId(operationCommand).orElse(null);
    if (duplicate != null) return duplicate.getAmount();

    UUID operationId = operationId(operationCommand);
    Instant now = clock.instant();
    BigDecimal positionBefore = position.getPrincipalBalance();
    BigDecimal positionAfter = positionBefore.subtract(deficit);
    updateCasa(lockedAccount.getId(), deficit, true);
    position.setPrincipalBalance(positionAfter);
    position.setUpdatedAt(now);
    positionRepository.saveAndFlush(position);

    String statementRef = "ASO-OUT-" + operationId;
    LedgerEntryEntity statement = statement(
        lockedAccount.getId(), "CREDIT", deficit, statementRef,
        "Tu dong rut tien gui de thanh toan", now);
    ledgerEntryRepository.saveAndFlush(statement);
    UUID journalId = journalService.recordInternalTransfer(
        journalCommand(operationCommand),
        journalReference(paymentReference, statementRef),
        "AUTO_SWEEP_OUT",
        null,
        "AUTO_SWEEP_POSITION:" + position.getId(),
        lockedAccount.getId(),
        "ACCOUNT:" + lockedAccount.getId(),
        deficit,
        lockedAccount.getCurrency(),
        "Automatic flexible deposit withdrawal for payment");
    BalanceState after = balance(lockedAccount.getId());
    operationRepository.saveAndFlush(operation(
        operationId, profile, position, "SWEEP_OUT", "PAYMENT", deficit, null,
        businessDate, operationCommand, truncate(paymentReference, 128),
        journalId, statement.getId(), balance.booked(), after.booked(),
        positionBefore, positionAfter, now));
    return deficit;
  }

  /** Accrues interest through the business date, then sweeps CASA excess for the next value day. */
  @Transactional
  public BigDecimal processEndOfDay(UUID profileId, LocalDate businessDate) {
    AutoSweepProfileEntity snapshot = profileRepository.findById(profileId)
        .orElseThrow(() -> new BusinessException(
            "AUTO_SWEEP_NOT_FOUND", "Auto-sweep profile not found"));
    AccountEntity account = accountRepository.findByIdForUpdate(snapshot.getSourceAccountId())
        .orElseThrow(() -> new BusinessException("ACCOUNT_NOT_FOUND", "Account not found"));
    AutoSweepProfileEntity profile = profileRepository.findByIdForUpdate(profileId)
        .orElseThrow(() -> new BusinessException(
            "AUTO_SWEEP_NOT_FOUND", "Auto-sweep profile not found"));
    if (!"ENABLED".equals(profile.getStatus())) return BigDecimal.ZERO;
    if (!"ACTIVE".equalsIgnoreCase(account.getStatus())
        || !"PAYMENT".equalsIgnoreCase(account.getAccountType())) return BigDecimal.ZERO;

    AutoSweepPositionEntity position = positionRepository
        .findByProfileIdForUpdate(profileId)
        .orElseThrow(() -> new BusinessException(
            "AUTO_SWEEP_POSITION_NOT_FOUND", "Sweep position not found"));
    SweepProductEntity product = productRepository.findById(profile.getProductCode())
        .orElseThrow(() -> new BusinessException(
            "SWEEP_PRODUCT_NOT_FOUND", "Sweep product not found"));
    accrue(profile, position, product, account, businessDate, "EOD");

    BalanceState before = balance(account.getId());
    BigDecimal amount = before.available().subtract(profile.getThresholdAmount());
    if (amount.compareTo(profile.getMinSweepAmount()) < 0) {
      profile.setLastSweepBusinessDate(businessDate);
      profile.setUpdatedAt(clock.instant());
      profileRepository.save(profile);
      return BigDecimal.ZERO;
    }
    if (product.getMaxPositionAmount() != null) {
      BigDecimal remaining = product.getMaxPositionAmount().subtract(position.getPrincipalBalance());
      amount = amount.min(remaining);
    }
    if (amount.compareTo(profile.getMinSweepAmount()) < 0) {
      profile.setLastSweepBusinessDate(businessDate);
      profile.setUpdatedAt(clock.instant());
      profileRepository.save(profile);
      return BigDecimal.ZERO;
    }
    String commandId = "SWEEP_IN:" + profileId + ":" + businessDate + ":" + amountKey(amount);
    if (operationRepository.findByCommandId(commandId).isPresent()) return BigDecimal.ZERO;

    UUID operationId = operationId(commandId);
    Instant now = clock.instant();
    BigDecimal positionBefore = position.getPrincipalBalance();
    BigDecimal positionAfter = positionBefore.add(amount);
    updateCasa(account.getId(), amount, false);
    position.setPrincipalBalance(positionAfter);
    position.setUpdatedAt(now);
    positionRepository.saveAndFlush(position);
    profile.setLastSweepBusinessDate(businessDate);
    profile.setUpdatedAt(now);
    profileRepository.saveAndFlush(profile);

    String statementRef = "ASO-IN-" + operationId;
    LedgerEntryEntity statement = statement(
        account.getId(), "DEBIT", amount, statementRef,
        "Tu dong chuyen tien nhan roi", now);
    ledgerEntryRepository.saveAndFlush(statement);
    UUID journalId = journalService.recordInternalTransfer(
        journalCommand(commandId), statementRef, "AUTO_SWEEP_IN",
        account.getId(), "ACCOUNT:" + account.getId(),
        null, "AUTO_SWEEP_POSITION:" + position.getId(),
        amount, account.getCurrency(), "End-of-day CASA auto-sweep");
    BalanceState after = balance(account.getId());
    operationRepository.saveAndFlush(operation(
        operationId, profile, position, "SWEEP_IN", "EOD", amount, null,
        businessDate, commandId, null, journalId, statement.getId(),
        before.booked(), after.booked(), positionBefore, positionAfter, now));
    return amount;
  }

  private void accrue(
      AutoSweepProfileEntity profile,
      AutoSweepPositionEntity position,
      SweepProductEntity product,
      AccountEntity account,
      LocalDate businessDate,
      String triggerType) {
    long days = ChronoUnit.DAYS.between(position.getLastAccrualDate(), businessDate);
    if (days <= 0) return;
    BigDecimal interest = AutoSweepInterestCalculator.calculate(
        position.getPrincipalBalance(), product.getAnnualRateBps(), days);
    position.setLastAccrualDate(businessDate);
    position.setUpdatedAt(clock.instant());
    if (interest.signum() <= 0) {
      positionRepository.save(position);
      return;
    }
    String commandId = "SWEEP_INTEREST:" + position.getId() + ":" + businessDate
        + ":" + amountKey(interest);
    if (operationRepository.findByCommandId(commandId).isPresent()) return;
    Instant now = clock.instant();
    BigDecimal positionBefore = position.getPrincipalBalance();
    BigDecimal positionAfter = positionBefore.add(interest);
    BigDecimal accruedAfter = position.getAccruedInterest().add(interest);
    position.setPrincipalBalance(positionAfter);
    position.setAccruedInterest(accruedAfter);
    positionRepository.saveAndFlush(position);
    UUID operationId = operationId(commandId);
    UUID journalId = journalService.recordInternalTransfer(
        journalCommand(commandId), commandId, "AUTO_SWEEP_INTEREST",
        null, "INTEREST_EXPENSE:" + account.getCurrency(),
        null, "AUTO_SWEEP_POSITION:" + position.getId(),
        interest, account.getCurrency(), "Daily flexible deposit interest accrual");
    BalanceState casa = balance(account.getId());
    operationRepository.saveAndFlush(operation(
        operationId, profile, position, "INTEREST_ACCRUAL", triggerType, interest,
        product.getAnnualRateBps(), businessDate, commandId, null, journalId, null,
        casa.booked(), casa.booked(), positionBefore, positionAfter, now));
  }

  private void updateCasa(UUID accountId, BigDecimal amount, boolean credit) {
    boolean updated = credit
        ? balanceRepository.creditIfActive(accountId, amount)
        : balanceRepository.debitIfAvailable(accountId, amount);
    if (!updated) {
      throw new BusinessException(
          "AUTO_SWEEP_BALANCE_CONFLICT", "CASA balance changed while auto-sweep was locked");
    }
  }

  private BalanceState balance(UUID accountId) {
    return balanceRepository.balance(accountId);
  }

  private LedgerEntryEntity statement(
      UUID accountId, String type, BigDecimal amount, String reference, String description, Instant now) {
    LedgerEntryEntity entry = new LedgerEntryEntity();
    entry.setId(UUID.randomUUID());
    entry.setAccountId(accountId);
    entry.setEntryType(type);
    entry.setAmount(amount);
    entry.setReferenceId(reference);
    entry.setDescription(description);
    entry.setCreatedAt(now);
    return entry;
  }

  private AutoSweepOperationEntity operation(
      UUID id, AutoSweepProfileEntity profile, AutoSweepPositionEntity position,
      String type, String trigger, BigDecimal amount, Integer rateBps,
      LocalDate date, String commandId, String paymentReference, UUID journalId,
      UUID statementEntryId, BigDecimal casaBefore, BigDecimal casaAfter,
      BigDecimal positionBefore, BigDecimal positionAfter, Instant now) {
    AutoSweepOperationEntity operation = new AutoSweepOperationEntity();
    operation.setId(id);
    operation.setProfileId(profile.getId());
    operation.setPositionId(position.getId());
    operation.setUserId(profile.getUserId());
    operation.setSourceAccountId(profile.getSourceAccountId());
    operation.setOperationType(type);
    operation.setTriggerType(trigger);
    operation.setAmount(amount);
    operation.setAnnualRateBps(rateBps);
    operation.setBusinessDate(date);
    operation.setCommandId(commandId);
    operation.setPaymentReference(paymentReference);
    operation.setJournalId(journalId);
    operation.setStatementEntryId(statementEntryId);
    operation.setCasaBalanceBefore(casaBefore);
    operation.setCasaBalanceAfter(casaAfter);
    operation.setPositionBalanceBefore(positionBefore);
    operation.setPositionBalanceAfter(positionAfter);
    operation.setCreatedAt(now);
    return operation;
  }

  private String paymentOperationCommand(
      UUID accountId, String paymentCommandId, BigDecimal requiredAmount) {
    if (paymentCommandId == null || paymentCommandId.isBlank()) {
      throw new BusinessException(
          "AUTO_SWEEP_COMMAND_ID_REQUIRED", "Payment command id is required for auto-sweep");
    }
    String amount = amountKey(requiredAmount);
    String raw = accountId + "|" + paymentCommandId.trim() + "|" + amount;
    UUID hash = UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8));
    return "SWEEP_OUT:" + accountId + ":" + hash;
  }

  private UUID operationId(String commandId) {
    return UUID.nameUUIDFromBytes(commandId.getBytes(StandardCharsets.UTF_8));
  }

  private String amountKey(BigDecimal amount) {
    return amount.stripTrailingZeros().toPlainString();
  }

  private String journalCommand(String operationCommand) {
    return "JOURNAL:" + operationCommand;
  }

  private String journalReference(String preferred, String fallback) {
    String value = preferred == null || preferred.isBlank() ? fallback : preferred;
    return value.length() <= 100 ? value : value.substring(0, 100);
  }

  private String truncate(String value, int maxLength) {
    if (value == null || value.length() <= maxLength) return value;
    return value.substring(0, maxLength);
  }
}
