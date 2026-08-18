package com.banksystem.account.application.ledger;

import com.banksystem.account.domain.account.AccountEntity;
import com.banksystem.account.domain.account.AccountRepository;
import com.banksystem.account.domain.ledger.AccountHoldRepository;
import com.banksystem.account.domain.ledger.AccountTemporalSnapshotEntity;
import com.banksystem.account.domain.ledger.AccountTemporalSnapshotRepository;
import com.banksystem.account.domain.ledger.LedgerEntryRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountTemporalSnapshotService {
  private final AccountTemporalSnapshotRepository snapshotRepository;
  private final AccountRepository accountRepository;
  private final LedgerEntryRepository entryRepository;
  private final AccountHoldRepository holdRepository;
  private final Clock clock;

  public AccountTemporalSnapshotService(
      AccountTemporalSnapshotRepository snapshotRepository,
      AccountRepository accountRepository,
      LedgerEntryRepository entryRepository,
      AccountHoldRepository holdRepository,
      Clock clock) {
    this.snapshotRepository = snapshotRepository;
    this.accountRepository = accountRepository;
    this.entryRepository = entryRepository;
    this.holdRepository = holdRepository;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public Optional<SnapshotState> nearest(UUID accountId, Instant at) {
    return snapshotRepository.findTopByAccountIdAndSnapshotAtLessThanEqualOrderBySnapshotAtDesc(accountId, at)
        .filter(this::valid)
        .map(snapshot -> new SnapshotState(
            snapshot.getSnapshotAt(), snapshot.getLedgerBalance(), snapshot.getActiveHoldAmount(),
            snapshot.getLastEntryAt(), snapshot.getSchemaVersion()));
  }

  @Transactional
  public int capturePage(int page, int size) {
    Instant at = clock.instant();
    var accounts = accountRepository.findAll(PageRequest.of(page, size));
    for (AccountEntity account : accounts) {
      BigDecimal held = holdRepository.activeAmountAt(account.getId(), at);
      Instant lastEntryAt = entryRepository.latestEntryAt(account.getId(), at);
      String checksum = checksum(
          account.getId(), at, account.getBalance(), held, lastEntryAt, 1);
      snapshotRepository.save(AccountTemporalSnapshotEntity.create(
          account.getId(), at, account.getBalance(), held, lastEntryAt, checksum));
    }
    return accounts.getNumberOfElements();
  }

  public BigDecimal replayDelta(UUID accountId, Instant after, Instant at) {
    return entryRepository.netMovement(accountId, after, at);
  }

  private boolean valid(AccountTemporalSnapshotEntity snapshot) {
    if (snapshot.getSchemaVersion() != 1) return false;
    return checksum(snapshot.getAccountId(), snapshot.getSnapshotAt(), snapshot.getLedgerBalance(),
        snapshot.getActiveHoldAmount(), snapshot.getLastEntryAt(), snapshot.getSchemaVersion())
        .equals(snapshot.getChecksum());
  }

  private String checksum(
      UUID accountId, Instant at, BigDecimal balance, BigDecimal held,
      Instant lastEntryAt, int version) {
    String canonical = accountId + "|" + at + "|" + balance.toPlainString() + "|"
        + held.toPlainString() + "|" + (lastEntryAt == null ? "" : lastEntryAt) + "|" + version;
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  public record SnapshotState(
      Instant at, BigDecimal balance, BigDecimal held, Instant lastEntryAt, int schemaVersion) {}
}
