package com.banksystem.transaction.application.settlement;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.transaction.domain.settlement.B2bPayoutStatus;
import com.banksystem.transaction.infrastructure.feign.AccountAtomicLedgerClient;
import com.banksystem.transaction.infrastructure.feign.AccountAtomicLedgerClient.AtomicPostingView;
import com.banksystem.transaction.infrastructure.feign.AccountAtomicLedgerClient.PayoutClearingCommand;
import com.banksystem.transaction.infrastructure.napas.NapasSwitchClient;
import com.banksystem.transaction.infrastructure.napas.NapasSwitchClient.NapasPaymentResponse;
import com.banksystem.transaction.infrastructure.napas.NapasSwitchClient.ProviderOutcome;
import java.math.BigDecimal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NapasPayoutExecutor {

  private static final Logger log = LoggerFactory.getLogger(NapasPayoutExecutor.class);

  private final NapasSwitchClient napasSwitchClient;
  private final AccountAtomicLedgerClient accountAtomicLedgerClient;
  private final PayoutResultTransactionService payoutResultTxService;
  private final SettlementTransactionService settlementTxService;

  @Value("${bank.payout.seller-payable-account-code}")
  private String sellerPayableAccountCode;

  @Value("${bank.payout.clearing-account-code}")
  private String clearingAccountCode;

  public NapasPayoutExecutor(
      NapasSwitchClient napasSwitchClient,
      AccountAtomicLedgerClient accountAtomicLedgerClient,
      PayoutResultTransactionService payoutResultTxService,
      SettlementTransactionService settlementTxService) {
    this.napasSwitchClient = napasSwitchClient;
    this.accountAtomicLedgerClient = accountAtomicLedgerClient;
    this.payoutResultTxService = payoutResultTxService;
    this.settlementTxService = settlementTxService;
  }

  public void executePayout(PayoutClaimContext ctx) {
    log.info("[NAPAS-EXECUTOR] Executing NAPAS payout [{}] requestId [{}] amount {} to bankBin [{}] acc [{}]",
        ctx.payoutId(), ctx.clientRequestId(), ctx.amount(), ctx.beneficiaryBankBin(), ctx.beneficiaryAccountNumber());

    try {
      NapasPaymentResponse resp = napasSwitchClient.executePayment(
          sellerPayableAccountCode,
          ctx.beneficiaryBankBin(),
          ctx.beneficiaryAccountNumber(),
          ctx.amount(),
          "Payout " + ctx.clientRequestId(),
          ctx.clientRequestId()
      );

      if (resp.outcome() == ProviderOutcome.SUCCESS) {
        log.info("[NAPAS-EXECUTOR] Payout [{}] returned SUCCESS from NAPAS (ref={})", ctx.payoutId(), resp.napasRefId());
        // Phase 1 (TX): Save switch success state
        if (!payoutResultTxService.markSwitchSuccess(ctx.payoutId(), ctx.claimToken(), resp.napasRefId())) {
          return;
        }

        // Phase 2 (Remote Call OUTSIDE DB TX): Post clearing ledger
        postClearingAndFinalize(ctx);
      } else if (resp.outcome() == ProviderOutcome.FAILED) {
        log.warn("[NAPAS-EXECUTOR] Payout [{}] returned FAILED from NAPAS: {}", ctx.payoutId(), resp.responseMessage());
        payoutResultTxService.markFailed(ctx.payoutId(), ctx.claimToken(), resp.responseMessage());
      } else {
        log.warn("[NAPAS-EXECUTOR] Payout [{}] returned outcome {} from NAPAS, marking PENDING_RECON",
            ctx.payoutId(), resp.outcome());
        payoutResultTxService.markPendingRecon(ctx.payoutId(), ctx.claimToken(), "NAPAS outcome: " + resp.outcome());
      }
    } catch (Exception ex) {
      log.error("[NAPAS-EXECUTOR] Payout [{}] execution threw exception: {}, marking PENDING_RECON",
          ctx.payoutId(), ex.getMessage());
      payoutResultTxService.markPendingRecon(ctx.payoutId(), ctx.claimToken(), ex.getMessage());
    }
  }

  public void inquireAndReconcile(PayoutClaimContext ctx) {
    log.info("[NAPAS-EXECUTOR] Reconciling payout [{}] with status [{}]", ctx.payoutId(), ctx.status());

    if (ctx.status() == B2bPayoutStatus.SWITCH_SUCCESS_LEDGER_PENDING) {
      // Only retry clearing ledger, DO NOT call NAPAS transfer again
      log.info("[NAPAS-EXECUTOR] Payout [{}] retrying clearing ledger ONLY", ctx.payoutId());
      postClearingAndFinalize(ctx);
      return;
    }

    if (ctx.status() == B2bPayoutStatus.PENDING_RECON) {
      try {
        NapasPaymentResponse inquiryResp = napasSwitchClient.inquirePayment(ctx.clientRequestId(), null);
        if (inquiryResp.outcome() == ProviderOutcome.SUCCESS) {
          log.info("[NAPAS-EXECUTOR] Payout [{}] inquiry returned SUCCESS (ref={})", ctx.payoutId(), inquiryResp.napasRefId());
          if (payoutResultTxService.markSwitchSuccess(
              ctx.payoutId(), ctx.claimToken(), inquiryResp.napasRefId())) {
            postClearingAndFinalize(ctx);
          }
        } else if ("NOT_FOUND".equalsIgnoreCase(inquiryResp.responseCode())
            || "NOT_ACCEPTED".equalsIgnoreCase(inquiryResp.responseCode())) {
          log.info("[NAPAS-EXECUTOR] Payout [{}] inquiry confirmed transaction not processed, resetting to READY with same clientRequestId", ctx.payoutId());
          payoutResultTxService.resetToReadyForRetry(ctx.payoutId(), ctx.claimToken());
        } else if (inquiryResp.outcome() == ProviderOutcome.FAILED) {
          log.warn("[NAPAS-EXECUTOR] Payout [{}] inquiry returned FAILED: {}", ctx.payoutId(), inquiryResp.responseMessage());
          payoutResultTxService.markFailed(ctx.payoutId(), ctx.claimToken(), inquiryResp.responseMessage());
        } else {
          log.warn("[NAPAS-EXECUTOR] Payout [{}] inquiry indeterminate ({}), remaining in PENDING_RECON",
              ctx.payoutId(), inquiryResp.outcome());
          payoutResultTxService.markPendingRecon(
              ctx.payoutId(), ctx.claimToken(), "Inquiry indeterminate: " + inquiryResp.outcome());
        }
      } catch (Exception ex) {
        log.error("[NAPAS-EXECUTOR] Payout [{}] inquiry failed: {}", ctx.payoutId(), ex.getMessage());
        payoutResultTxService.markPendingRecon(
            ctx.payoutId(), ctx.claimToken(), "Inquiry error: " + ex.getMessage());
      }
    }
  }

  private void postClearingAndFinalize(PayoutClaimContext ctx) {
    UUID payoutId = ctx.payoutId();
    String clearingCommandId = "PAYOUT_CLEARING:" + payoutId;
    PayoutClearingCommand command = new PayoutClearingCommand(
        clearingCommandId,
        "PAYOUT:" + payoutId,
        payoutId,
        sellerPayableAccountCode,
        clearingAccountCode,
        ctx.amount(),
        ctx.currency(),
        "Payout clearing posting"
    );

    try {
      ApiResponse<AtomicPostingView> resp = accountAtomicLedgerClient.recordPayoutClearing(command);
      if (resp != null && resp.data() != null && resp.data().journalId() != null
          && clearingCommandId.equals(resp.data().businessCommandId())
          && ctx.amount().compareTo(resp.data().amount()) == 0) {
        UUID journalId = resp.data().journalId();
        UUID settlementId = payoutResultTxService.markClearingSuccess(
            payoutId, ctx.claimToken(), journalId);
        if (settlementId != null) {
          settlementTxService.completeSettlementIfAllLegsDone(settlementId);
        }
      } else {
        log.error("[NAPAS-EXECUTOR] Payout [{}] clearing ledger returned an indeterminate or mismatched result", payoutId);
      }
    } catch (Exception ex) {
      log.error("[NAPAS-EXECUTOR] Payout [{}] clearing ledger recording failed: {}. Preserving SWITCH_SUCCESS_LEDGER_PENDING",
          payoutId, ex.getMessage());
    }
  }
}
