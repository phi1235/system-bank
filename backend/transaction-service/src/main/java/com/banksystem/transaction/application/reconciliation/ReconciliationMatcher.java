package com.banksystem.transaction.application.reconciliation;

import com.banksystem.transaction.domain.transfer.TransferOrderEntity;
import com.banksystem.transaction.domain.transfer.TransferStatus;
import com.banksystem.transaction.infrastructure.feign.LedgerClientDtos.LedgerEntryView;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Pure matching rules for end-of-day reconciliation. No I/O — takes the day's transfer orders and
 * the ledger entries pulled from account-service, returns every discrepancy found.
 *
 * <p>Ledger reference contract (see {@code TransferSagaOrchestrator} / {@code
 * TransferFeeGlService}): source debit and dest credit use {@code ref = transferId}; auxiliary
 * postings use {@code transferId + "-fee" | "-compensation" | "-reverse-dest"}.
 *
 * <p>Out of scope (documented, not silent): fee-income account identity is only known by account
 * number, so the fee posting is matched by reference+type without an account check.
 */
@Component
public class ReconciliationMatcher {

  public static final String MISSING_DEBIT = "MISSING_DEBIT";
  public static final String MISSING_CREDIT = "MISSING_CREDIT";
  public static final String MISSING_FEE_POSTING = "MISSING_FEE_POSTING";
  public static final String MISSING_REFUND = "MISSING_REFUND";
  public static final String MISSING_DEST_REVERSAL = "MISSING_DEST_REVERSAL";
  public static final String AMOUNT_MISMATCH_DEBIT = "AMOUNT_MISMATCH_DEBIT";
  public static final String AMOUNT_MISMATCH_CREDIT = "AMOUNT_MISMATCH_CREDIT";
  public static final String AMOUNT_MISMATCH_FEE = "AMOUNT_MISMATCH_FEE";
  public static final String AMOUNT_MISMATCH_REFUND = "AMOUNT_MISMATCH_REFUND";
  public static final String UNEXPECTED_COMPENSATION = "UNEXPECTED_COMPENSATION";
  public static final String UNEXPECTED_DEBIT_FOR_FAILED = "UNEXPECTED_DEBIT_FOR_FAILED";
  public static final String STALE_IN_FLIGHT = "STALE_IN_FLIGHT";
  public static final String PROVIDER_OUTCOME_UNKNOWN = "PROVIDER_OUTCOME_UNKNOWN";
  public static final String MANUAL_REVIEW_REQUIRED = "MANUAL_REVIEW_REQUIRED";

  static final String SUFFIX_FEE = "-fee";
  static final String SUFFIX_COMPENSATION = "-compensation";
  static final String SUFFIX_REVERSE_DEST = "-reverse-dest";

  public record Discrepancy(
      UUID transferId,
      String kind,
      String entryRef,
      BigDecimal expectedAmount,
      BigDecimal actualAmount,
      String detail) {}

  public List<Discrepancy> match(
      List<TransferOrderEntity> transfers, List<LedgerEntryView> entries) {
    Map<String, List<LedgerEntryView>> byRef =
        entries.stream().collect(Collectors.groupingBy(LedgerEntryView::referenceId));
    List<Discrepancy> out = new ArrayList<>();
    for (TransferOrderEntity t : transfers) {
      switch (t.getStatus()) {
        case COMPLETED -> checkCompleted(t, byRef, out);
        case COMPENSATED -> checkCompensated(t, byRef, out);
        case FAILED -> checkFailed(t, byRef, out);
        case PENDING, DEBITED, COMPENSATING ->
            out.add(
                new Discrepancy(
                    t.getId(),
                    STALE_IN_FLIGHT,
                    t.getId().toString(),
                    null,
                    null,
                    "Still " + t.getStatus() + " at reconciliation time"));
        case UNKNOWN -> out.add(
            new Discrepancy(
                t.getId(), PROVIDER_OUTCOME_UNKNOWN, t.getProviderReferenceId(), null, null,
                "External provider outcome is still unknown"));
        case REVIEW_REQUIRED, RISK_REVIEW -> out.add(
            new Discrepancy(
                t.getId(), MANUAL_REVIEW_REQUIRED, t.getProviderReferenceId(), null, null,
                t.getFailureReason()));
      }
    }
    return out;
  }

  private void checkCompleted(
      TransferOrderEntity t, Map<String, List<LedgerEntryView>> byRef, List<Discrepancy> out) {
    String ref = t.getId().toString();
    BigDecimal debitTotal = t.getAmount().add(fee(t));

    Optional<LedgerEntryView> debit = find(byRef, ref, "DEBIT", t.getFromAccountId());
    if (debit.isEmpty()) {
      out.add(new Discrepancy(t.getId(), MISSING_DEBIT, ref, debitTotal, null,
          "No source debit in ledger"));
    } else if (differs(debitTotal, debit.get().amount())) {
      out.add(new Discrepancy(t.getId(), AMOUNT_MISMATCH_DEBIT, ref, debitTotal,
          debit.get().amount(), "Source debit amount differs (principal + fee expected)"));
    }

    if (t.getToAccountId() != null) {
      Optional<LedgerEntryView> credit = find(byRef, ref, "CREDIT", t.getToAccountId());
      if (credit.isEmpty()) {
        out.add(new Discrepancy(t.getId(), MISSING_CREDIT, ref, t.getAmount(), null,
            "No destination credit in ledger"));
      } else if (differs(t.getAmount(), credit.get().amount())) {
        out.add(new Discrepancy(t.getId(), AMOUNT_MISMATCH_CREDIT, ref, t.getAmount(),
            credit.get().amount(), "Destination credit amount differs (principal expected)"));
      }
    }

    if (fee(t).signum() > 0) {
      String feeRef = ref + SUFFIX_FEE;
      Optional<LedgerEntryView> feeEntry = find(byRef, feeRef, "CREDIT", null);
      if (feeEntry.isEmpty()) {
        out.add(new Discrepancy(t.getId(), MISSING_FEE_POSTING, feeRef, fee(t), null,
            "No fee-income credit in ledger"));
      } else if (differs(fee(t), feeEntry.get().amount())) {
        out.add(new Discrepancy(t.getId(), AMOUNT_MISMATCH_FEE, feeRef, fee(t),
            feeEntry.get().amount(), "Fee-income credit amount differs"));
      }
    }

    for (String suffix : List.of(SUFFIX_COMPENSATION, SUFFIX_REVERSE_DEST)) {
      String compRef = ref + suffix;
      if (byRef.containsKey(compRef)) {
        out.add(new Discrepancy(t.getId(), UNEXPECTED_COMPENSATION, compRef, null,
            byRef.get(compRef).get(0).amount(),
            "Compensation posting exists for a COMPLETED transfer"));
      }
    }
  }

  private void checkCompensated(
      TransferOrderEntity t, Map<String, List<LedgerEntryView>> byRef, List<Discrepancy> out) {
    String ref = t.getId().toString();
    BigDecimal debitTotal = t.getAmount().add(fee(t));

    // Money only needs to come back if it actually left.
    if (find(byRef, ref, "DEBIT", t.getFromAccountId()).isPresent()) {
      String refundRef = ref + SUFFIX_COMPENSATION;
      Optional<LedgerEntryView> refund = find(byRef, refundRef, "CREDIT", t.getFromAccountId());
      if (refund.isEmpty()) {
        out.add(new Discrepancy(t.getId(), MISSING_REFUND, refundRef, debitTotal, null,
            "Source was debited but never refunded"));
      } else if (differs(debitTotal, refund.get().amount())) {
        out.add(new Discrepancy(t.getId(), AMOUNT_MISMATCH_REFUND, refundRef, debitTotal,
            refund.get().amount(), "Refund amount differs from original debit"));
      }
    }

    if (t.getToAccountId() != null
        && find(byRef, ref, "CREDIT", t.getToAccountId()).isPresent()) {
      String reverseRef = ref + SUFFIX_REVERSE_DEST;
      if (find(byRef, reverseRef, "DEBIT", t.getToAccountId()).isEmpty()) {
        out.add(new Discrepancy(t.getId(), MISSING_DEST_REVERSAL, reverseRef, t.getAmount(), null,
            "Destination was credited but never reversed"));
      }
    }
  }

  private void checkFailed(
      TransferOrderEntity t, Map<String, List<LedgerEntryView>> byRef, List<Discrepancy> out) {
    String ref = t.getId().toString();
    Optional<LedgerEntryView> debit = find(byRef, ref, "DEBIT", t.getFromAccountId());
    if (debit.isPresent()) {
      out.add(new Discrepancy(t.getId(), UNEXPECTED_DEBIT_FOR_FAILED, ref, null,
          debit.get().amount(), "Ledger debit exists for a FAILED (never-debited) transfer"));
    }
  }

  private static Optional<LedgerEntryView> find(
      Map<String, List<LedgerEntryView>> byRef, String ref, String entryType, UUID accountId) {
    return byRef.getOrDefault(ref, List.of()).stream()
        .filter(e -> entryType.equalsIgnoreCase(e.entryType()))
        .filter(e -> accountId == null || accountId.toString().equals(e.accountId()))
        .findFirst();
  }

  private static BigDecimal fee(TransferOrderEntity t) {
    return t.getFeeAmount() == null ? BigDecimal.ZERO : t.getFeeAmount();
  }

  private static boolean differs(BigDecimal expected, BigDecimal actual) {
    return actual == null || expected.compareTo(actual) != 0;
  }
}
