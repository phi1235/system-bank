package com.banksystem.transaction.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.banksystem.transaction.application.ReconciliationMatcher.Discrepancy;
import com.banksystem.transaction.domain.TransferOrderEntity;
import com.banksystem.transaction.domain.TransferStatus;
import com.banksystem.transaction.infrastructure.feign.LedgerClientDtos.LedgerEntryView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReconciliationMatcherTest {

  private static final Instant TS = Instant.parse("2026-07-25T10:00:00Z");

  private final ReconciliationMatcher matcher = new ReconciliationMatcher();

  private final UUID source = UUID.randomUUID();
  private final UUID dest = UUID.randomUUID();
  private final UUID feeIncome = UUID.randomUUID();

  @Test
  void completedTransferFullyMatchedProducesNoDiscrepancies() {
    TransferOrderEntity t = transfer(TransferStatus.COMPLETED, "1000.00", "10.00");
    List<Discrepancy> out =
        matcher.match(
            List.of(t),
            List.of(
                entry(source, "DEBIT", "1010.00", ref(t)),
                entry(dest, "CREDIT", "1000.00", ref(t)),
                entry(feeIncome, "CREDIT", "10.00", ref(t) + "-fee")));
    assertEquals(List.of(), out);
  }

  @Test
  void completedZeroFeeNeedsNoFeePosting() {
    TransferOrderEntity t = transfer(TransferStatus.COMPLETED, "500.00", "0.00");
    List<Discrepancy> out =
        matcher.match(
            List.of(t),
            List.of(
                entry(source, "DEBIT", "500.00", ref(t)),
                entry(dest, "CREDIT", "500.00", ref(t))));
    assertEquals(List.of(), out);
  }

  @Test
  void completedMissingDebitIsReported() {
    TransferOrderEntity t = transfer(TransferStatus.COMPLETED, "1000.00", "10.00");
    List<Discrepancy> out =
        matcher.match(
            List.of(t),
            List.of(
                entry(dest, "CREDIT", "1000.00", ref(t)),
                entry(feeIncome, "CREDIT", "10.00", ref(t) + "-fee")));
    assertEquals(1, out.size());
    assertEquals(ReconciliationMatcher.MISSING_DEBIT, out.get(0).kind());
    assertEquals(new BigDecimal("1010.00"), out.get(0).expectedAmount());
  }

  @Test
  void completedDebitMustIncludeFee() {
    TransferOrderEntity t = transfer(TransferStatus.COMPLETED, "1000.00", "10.00");
    List<Discrepancy> out =
        matcher.match(
            List.of(t),
            List.of(
                // Debited principal only — fee never charged
                entry(source, "DEBIT", "1000.00", ref(t)),
                entry(dest, "CREDIT", "1000.00", ref(t)),
                entry(feeIncome, "CREDIT", "10.00", ref(t) + "-fee")));
    assertEquals(1, out.size());
    assertEquals(ReconciliationMatcher.AMOUNT_MISMATCH_DEBIT, out.get(0).kind());
  }

  @Test
  void completedCreditAmountMismatchIsReported() {
    TransferOrderEntity t = transfer(TransferStatus.COMPLETED, "1000.00", "0.00");
    List<Discrepancy> out =
        matcher.match(
            List.of(t),
            List.of(
                entry(source, "DEBIT", "1000.00", ref(t)),
                entry(dest, "CREDIT", "999.99", ref(t))));
    assertEquals(1, out.size());
    assertEquals(ReconciliationMatcher.AMOUNT_MISMATCH_CREDIT, out.get(0).kind());
    assertEquals(new BigDecimal("999.99"), out.get(0).actualAmount());
  }

  @Test
  void completedCreditOnWrongAccountCountsAsMissing() {
    TransferOrderEntity t = transfer(TransferStatus.COMPLETED, "1000.00", "0.00");
    List<Discrepancy> out =
        matcher.match(
            List.of(t),
            List.of(
                entry(source, "DEBIT", "1000.00", ref(t)),
                entry(UUID.randomUUID(), "CREDIT", "1000.00", ref(t))));
    assertEquals(1, out.size());
    assertEquals(ReconciliationMatcher.MISSING_CREDIT, out.get(0).kind());
  }

  @Test
  void completedMissingFeePostingIsReported() {
    TransferOrderEntity t = transfer(TransferStatus.COMPLETED, "1000.00", "10.00");
    List<Discrepancy> out =
        matcher.match(
            List.of(t),
            List.of(
                entry(source, "DEBIT", "1010.00", ref(t)),
                entry(dest, "CREDIT", "1000.00", ref(t))));
    assertEquals(1, out.size());
    assertEquals(ReconciliationMatcher.MISSING_FEE_POSTING, out.get(0).kind());
  }

  @Test
  void completedWithCompensationEntryIsSuspicious() {
    TransferOrderEntity t = transfer(TransferStatus.COMPLETED, "1000.00", "0.00");
    List<Discrepancy> out =
        matcher.match(
            List.of(t),
            List.of(
                entry(source, "DEBIT", "1000.00", ref(t)),
                entry(dest, "CREDIT", "1000.00", ref(t)),
                entry(source, "CREDIT", "1000.00", ref(t) + "-compensation")));
    assertEquals(1, out.size());
    assertEquals(ReconciliationMatcher.UNEXPECTED_COMPENSATION, out.get(0).kind());
  }

  @Test
  void compensatedWithFullRefundIsClean() {
    TransferOrderEntity t = transfer(TransferStatus.COMPENSATED, "1000.00", "10.00");
    List<Discrepancy> out =
        matcher.match(
            List.of(t),
            List.of(
                entry(source, "DEBIT", "1010.00", ref(t)),
                entry(source, "CREDIT", "1010.00", ref(t) + "-compensation")));
    assertEquals(List.of(), out);
  }

  @Test
  void compensatedDebitWithoutRefundIsCritical() {
    TransferOrderEntity t = transfer(TransferStatus.COMPENSATED, "1000.00", "10.00");
    List<Discrepancy> out =
        matcher.match(List.of(t), List.of(entry(source, "DEBIT", "1010.00", ref(t))));
    assertEquals(1, out.size());
    assertEquals(ReconciliationMatcher.MISSING_REFUND, out.get(0).kind());
    assertEquals(new BigDecimal("1010.00"), out.get(0).expectedAmount());
  }

  @Test
  void compensatedNeverDebitedNeedsNothing() {
    TransferOrderEntity t = transfer(TransferStatus.COMPENSATED, "1000.00", "10.00");
    assertEquals(List.of(), matcher.match(List.of(t), List.of()));
  }

  @Test
  void compensatedCreditedDestMustBeReversed() {
    TransferOrderEntity t = transfer(TransferStatus.COMPENSATED, "1000.00", "0.00");
    List<Discrepancy> out =
        matcher.match(
            List.of(t),
            List.of(
                entry(source, "DEBIT", "1000.00", ref(t)),
                entry(source, "CREDIT", "1000.00", ref(t) + "-compensation"),
                entry(dest, "CREDIT", "1000.00", ref(t))));
    assertEquals(1, out.size());
    assertEquals(ReconciliationMatcher.MISSING_DEST_REVERSAL, out.get(0).kind());
  }

  @Test
  void failedTransferWithLedgerDebitIsCritical() {
    TransferOrderEntity t = transfer(TransferStatus.FAILED, "1000.00", "0.00");
    List<Discrepancy> out =
        matcher.match(List.of(t), List.of(entry(source, "DEBIT", "1000.00", ref(t))));
    assertEquals(1, out.size());
    assertEquals(ReconciliationMatcher.UNEXPECTED_DEBIT_FOR_FAILED, out.get(0).kind());
  }

  @Test
  void inFlightAtReconTimeIsStale() {
    TransferOrderEntity t = transfer(TransferStatus.DEBITED, "1000.00", "0.00");
    List<Discrepancy> out = matcher.match(List.of(t), List.of());
    assertEquals(1, out.size());
    assertEquals(ReconciliationMatcher.STALE_IN_FLIGHT, out.get(0).kind());
    assertTrue(out.get(0).detail().contains("DEBITED"));
  }

  @Test
  void externalTransferWithoutDestAccountSkipsCreditCheck() {
    TransferOrderEntity t = transfer(TransferStatus.COMPLETED, "1000.00", "0.00");
    t.setToAccountId(null);
    List<Discrepancy> out =
        matcher.match(List.of(t), List.of(entry(source, "DEBIT", "1000.00", ref(t))));
    assertEquals(List.of(), out);
  }

  private TransferOrderEntity transfer(TransferStatus status, String amount, String fee) {
    TransferOrderEntity t = new TransferOrderEntity();
    t.setId(UUID.randomUUID());
    t.setFromAccountId(source);
    t.setToAccountId(dest);
    t.setAmount(new BigDecimal(amount));
    t.setFeeAmount(new BigDecimal(fee));
    t.setStatus(status);
    return t;
  }

  private static LedgerEntryView entry(UUID accountId, String type, String amount, String ref) {
    return new LedgerEntryView(
        UUID.randomUUID().toString(), accountId.toString(), type, new BigDecimal(amount), ref, TS);
  }

  private static String ref(TransferOrderEntity t) {
    return t.getId().toString();
  }
}
