package com.banksystem.transaction.application.openbanking.impl;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.common.iso20022.Iso20022Engine;
import com.banksystem.common.iso20022.Pain001Dto;
import com.banksystem.common.iso20022.Pain001Dto.CreditTransferTransactionInformation;
import com.banksystem.common.iso20022.Pain001Dto.PaymentInformation;
import com.banksystem.common.iso20022.Pain002Dto;
import com.banksystem.common.iso20022.Pain002Dto.GroupHeader;
import com.banksystem.common.iso20022.Pain002Dto.OriginalPaymentInformationAndStatus;
import com.banksystem.common.iso20022.Pain002Dto.Reason;
import com.banksystem.common.iso20022.Pain002Dto.StatusReasonInformation;
import com.banksystem.common.iso20022.Pain002Dto.TransactionInformationAndStatus;
import com.banksystem.transaction.application.gateway.AccountGateway;
import com.banksystem.transaction.application.openbanking.OpenBankingPaymentService;
import com.banksystem.transaction.application.openbanking.OpenBankingRecordSearchQuery;
import com.banksystem.transaction.domain.openbanking.IsoPaymentMessageEntity;
import com.banksystem.transaction.domain.openbanking.IsoPaymentMessageRepository;
import com.banksystem.transaction.domain.openbanking.IsoPaymentRecordEntity;
import com.banksystem.transaction.domain.openbanking.IsoPaymentRecordRepository;
import com.banksystem.transaction.domain.outbox.OutboxEventEntity;
import com.banksystem.transaction.domain.outbox.OutboxEventRepository;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.AccountView;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.MoneyCommand;
import com.banksystem.transaction.infrastructure.feign.AccountClientDtos.MoneyResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpenBankingPaymentServiceImpl implements OpenBankingPaymentService {

  private static final Logger log = LoggerFactory.getLogger(OpenBankingPaymentServiceImpl.class);

  private final IsoPaymentMessageRepository messageRepository;
  private final IsoPaymentRecordRepository recordRepository;
  private final AccountGateway accountGateway;
  private final OutboxEventRepository outboxEventRepository;

  public OpenBankingPaymentServiceImpl(
      IsoPaymentMessageRepository messageRepository,
      IsoPaymentRecordRepository recordRepository,
      AccountGateway accountGateway,
      OutboxEventRepository outboxEventRepository) {
    this.messageRepository = messageRepository;
    this.recordRepository = recordRepository;
    this.accountGateway = accountGateway;
    this.outboxEventRepository = outboxEventRepository;
  }

  @Override
  @Transactional
  public Pain002Dto initiateSinglePayment(
      String clientId, Pain001Dto pain001, String signaturePayload) {
    validatePain001Structure(pain001);

    String rawPayload = serializeIsoPayload(pain001);
    String msgId = pain001.groupHeader().messageIdentification();
    PaymentInformation pmtInf = pain001.paymentInformation().get(0);
    String debtorAcc = pmtInf.debtorAccount().accountNumber();
    CreditTransferTransactionInformation txInf = pmtInf.creditTransferTransactionInformation().get(0);

    String instrId = txInf.paymentIdentification().instructionIdentification();
    String endToEndId = txInf.paymentIdentification().endToEndIdentification();
    BigDecimal amount = txInf.amount().value();
    String currency = txInf.amount().currency() != null ? txInf.amount().currency() : "VND";
    String creditorAcc = txInf.creditorAccount().accountNumber();
    String creditorBank = txInf.creditorAgent() != null ? txInf.creditorAgent().bankCode() : null;

    // 1. Check Pure Deterministic Idempotency Key
    Optional<IsoPaymentRecordEntity> existingRecord = recordRepository.findByClientIdAndEndToEndId(clientId, endToEndId);
    if (existingRecord.isPresent()) {
      log.info("Idempotent hit for B2B payment: client={}, endToEndId={}", clientId, endToEndId);
      return buildPain002Response(msgId, List.of(existingRecord.get()));
    }

    Instant now = Instant.now();

    // 2. Save Inbound ISO Message
    IsoPaymentMessageEntity messageEntity = IsoPaymentMessageEntity.create(
        UUID.randomUUID(),
        msgId,
        clientId,
        "PAIN_001",
        "INBOUND",
        1,
        amount,
        "PROCESSING",
        rawPayload,
        signaturePayload,
        signaturePayload != null && !signaturePayload.isBlank(),
        now
    );
    messageRepository.save(messageEntity);

    // 3. Process Debit & Credit Settlement
    String status = "ACSC";
    String reasonCode = "G000";
    String reasonDesc = "Transaction successfully settled and ledger committed.";
    UUID transferOrderId = UUID.randomUUID();

    try {
      AccountView debtorView = accountGateway.getAccountByNumber(debtorAcc);
      AccountView creditorView = accountGateway.getAccountByNumber(creditorAcc);

      MoneyCommand debitCmd = new MoneyCommand(amount, endToEndId, "Open Banking transfer to " + creditorAcc, transferOrderId.toString());
      MoneyResult debitResult = accountGateway.debit(debtorView.idUuid(), debitCmd);
      if (debitResult == null) {
        status = "RJCT";
        reasonCode = "AM04";
        reasonDesc = "Debit failed on debtor account";
      } else {
        MoneyCommand creditCmd = new MoneyCommand(amount, endToEndId, "Open Banking transfer from " + debtorAcc, transferOrderId.toString());
        MoneyResult creditResult = accountGateway.credit(creditorView.idUuid(), creditCmd);
        if (creditResult == null) {
          // Compensate debit
          accountGateway.compensateCredit(debtorView.idUuid(), debitCmd);
          status = "RJCT";
          reasonCode = "AC04";
          reasonDesc = "Creditor account credit failed";
        }
      }
    } catch (Exception ex) {
      log.error("Execution failed for Open Banking payment {}: {}", endToEndId, ex.getMessage());
      status = "RJCT";
      reasonCode = "RR04";
      reasonDesc = "Processing failure: " + ex.getMessage();
    }

    // 4. Save Payment Record
    IsoPaymentRecordEntity recordEntity = IsoPaymentRecordEntity.create(
        UUID.randomUUID(),
        msgId,
        clientId,
        instrId,
        endToEndId,
        debtorAcc,
        creditorAcc,
        creditorBank,
        amount,
        currency,
        status,
        reasonCode,
        reasonDesc,
        now
    );
    recordEntity.setTransferOrderId(transferOrderId);
    recordRepository.save(recordEntity);

    // 5. Update Message Status
    messageEntity.setOverallStatus("ACSC".equals(status) ? "COMPLETED" : "REJECTED");
    messageEntity.setUpdatedAt(Instant.now());
    messageRepository.save(messageEntity);

    // 6. Transactional Outbox Event for Webhook Dispatcher
    saveOutboxEvent(clientId, msgId, endToEndId, status, reasonCode, reasonDesc);

    return buildPain002Response(msgId, List.of(recordEntity));
  }

  @Override
  public Pain002Dto initiateBulkPayments(
      String clientId, Pain001Dto pain001, String signaturePayload) {
    validatePain001Structure(pain001);

    String rawPayload = serializeIsoPayload(pain001);
    String msgId = pain001.groupHeader().messageIdentification();
    PaymentInformation pmtInf = pain001.paymentInformation().get(0);
    String debtorAcc = pmtInf.debtorAccount().accountNumber();
    List<CreditTransferTransactionInformation> txList = pmtInf.creditTransferTransactionInformation();

    BigDecimal totalAmount = txList.stream()
        .map(t -> t.amount().value())
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    Instant now = Instant.now();

    IsoPaymentMessageEntity messageEntity = IsoPaymentMessageEntity.create(
        UUID.randomUUID(),
        msgId,
        clientId,
        "PAIN_001_BULK",
        "INBOUND",
        txList.size(),
        totalAmount,
        "PROCESSING",
        rawPayload,
        signaturePayload,
        signaturePayload != null && !signaturePayload.isBlank(),
        now
    );
    messageRepository.save(messageEntity);

    List<IsoPaymentRecordEntity> processedRecords = Collections.synchronizedList(new ArrayList<>());

    // High-Performance Batch Engine with Java 21 Virtual Threads
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<CompletableFuture<Void>> futures = txList.stream()
          .map(tx -> CompletableFuture.runAsync(() -> {
            String instrId = tx.paymentIdentification().instructionIdentification();
            String endToEndId = tx.paymentIdentification().endToEndIdentification();
            BigDecimal amount = tx.amount().value();
            String currency = tx.amount().currency() != null ? tx.amount().currency() : "VND";
            String creditorAcc = tx.creditorAccount().accountNumber();
            String creditorBank = tx.creditorAgent() != null ? tx.creditorAgent().bankCode() : null;

            String status = "ACSC";
            String reasonCode = "G000";
            String reasonDesc = "Bulk transaction successfully settled.";
            UUID transferOrderId = UUID.randomUUID();

            try {
              AccountView debtorView = accountGateway.getAccountByNumber(debtorAcc);
              AccountView creditorView = accountGateway.getAccountByNumber(creditorAcc);

              MoneyCommand debitCmd = new MoneyCommand(amount, endToEndId, "Bulk payout to " + creditorAcc, transferOrderId.toString());
              MoneyResult debitResult = accountGateway.debit(debtorView.idUuid(), debitCmd);
              if (debitResult == null) {
                status = "RJCT";
                reasonCode = "AM04";
                reasonDesc = "Insufficient funds in debtor account";
              } else {
                MoneyCommand creditCmd = new MoneyCommand(amount, endToEndId, "Bulk payout from " + debtorAcc, transferOrderId.toString());
                MoneyResult creditResult = accountGateway.credit(creditorView.idUuid(), creditCmd);
                if (creditResult == null) {
                  accountGateway.compensateCredit(debtorView.idUuid(), debitCmd);
                  status = "RJCT";
                  reasonCode = "AC04";
                  reasonDesc = "Creditor account closed";
                }
              }
            } catch (Exception ex) {
              status = "RJCT";
              reasonCode = "RR04";
              reasonDesc = "Error: " + ex.getMessage();
            }

            IsoPaymentRecordEntity rec = IsoPaymentRecordEntity.create(
                UUID.randomUUID(),
                msgId,
                clientId,
                instrId,
                endToEndId,
                debtorAcc,
                creditorAcc,
                creditorBank,
                amount,
                currency,
                status,
                reasonCode,
                reasonDesc,
                Instant.now()
            );
            rec.setTransferOrderId(transferOrderId);
            recordRepository.save(rec);
            processedRecords.add(rec);
          }, executor))
          .toList();

      CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    long successCount = processedRecords.stream().filter(r -> "ACSC".equals(r.getStatus())).count();
    String overallStatus;
    if (successCount == processedRecords.size()) {
      overallStatus = "COMPLETED";
    } else if (successCount > 0) {
      overallStatus = "PARTIALLY_COMPLETED";
    } else {
      overallStatus = "REJECTED";
    }

    messageEntity.setOverallStatus(overallStatus);
    messageEntity.setUpdatedAt(Instant.now());
    messageRepository.save(messageEntity);

    saveOutboxEvent(clientId, msgId, "BATCH-" + msgId, overallStatus, "G000", "Bulk payment finished with status: " + overallStatus);

    return buildPain002Response(msgId, processedRecords);
  }

  @Override
  @Transactional(readOnly = true)
  public Pain002Dto getPaymentStatus(String clientId, String paymentId) {
    Optional<IsoPaymentMessageEntity> messageOpt = messageRepository.findByMessageId(paymentId);
    if (messageOpt.isPresent()) {
      List<IsoPaymentRecordEntity> records = recordRepository.findByMessageId(paymentId);
      return buildPain002Response(paymentId, records);
    }

    Optional<IsoPaymentRecordEntity> recordOpt = recordRepository.findByClientIdAndEndToEndId(clientId, paymentId);
    if (recordOpt.isPresent()) {
      return buildPain002Response(recordOpt.get().getMessageId(), List.of(recordOpt.get()));
    }

    throw new BusinessException("PAYMENT_NOT_FOUND", "Payment message or record not found: " + paymentId, HttpStatus.NOT_FOUND);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<IsoPaymentRecordEntity> listPaymentRecords(OpenBankingRecordSearchQuery query) {
    return recordRepository.searchRecords(query.clientId(), query.messageId(), query.status(), query.toPageable());
  }

  private void validatePain001Structure(Pain001Dto pain001) {
    if (pain001 == null || pain001.groupHeader() == null || pain001.paymentInformation() == null || pain001.paymentInformation().isEmpty()) {
      throw new BusinessException("INVALID_ISO_MESSAGE", "Malformed pain.001 message structure", HttpStatus.BAD_REQUEST);
    }
    PaymentInformation pmtInf = pain001.paymentInformation().get(0);
    if (pmtInf.debtorAccount() == null || pmtInf.creditTransferTransactionInformation() == null || pmtInf.creditTransferTransactionInformation().isEmpty()) {
      throw new BusinessException("INVALID_ISO_PAYMENT_INFO", "Missing debtor account or transaction information", HttpStatus.BAD_REQUEST);
    }
  }

  private Pain002Dto buildPain002Response(String msgId, List<IsoPaymentRecordEntity> records) {
    String nowIso = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
    GroupHeader grpHdr = new GroupHeader("PSR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(), nowIso, msgId);

    List<TransactionInformationAndStatus> txStatusList = records.stream()
        .map(r -> new TransactionInformationAndStatus(
            "STAT-" + r.getId().toString().substring(0, 8),
            r.getInstructionId(),
            r.getEndToEndId(),
            r.getStatus(),
            new StatusReasonInformation(new Reason(r.getStatusReasonCode()), r.getStatusReasonDesc()),
            DateTimeFormatter.ISO_INSTANT.format(r.getCreatedAt()),
            "SYSB-TRF-" + (r.getTransferOrderId() != null ? r.getTransferOrderId().toString().substring(0, 8) : "N/A")
        ))
        .toList();

    String pmtStatus = txStatusList.stream().allMatch(t -> "ACSC".equals(t.transactionStatus())) ? "ACTC" : "PART";
    if (txStatusList.stream().allMatch(t -> "RJCT".equals(t.transactionStatus()))) {
      pmtStatus = "RJCT";
    }

    OriginalPaymentInformationAndStatus origInf = new OriginalPaymentInformationAndStatus(
        "PAY-INFO-" + msgId,
        pmtStatus,
        txStatusList
    );

    return new Pain002Dto(grpHdr, List.of(origInf));
  }

  private void saveOutboxEvent(
      String clientId, String msgId, String endToEndId, String status, String reasonCode, String reasonDesc) {
    try {
      String dedupeKey = "B2B-WEBHOOK-" + clientId + "-" + endToEndId + "-" + status;
      String payload = """
          {
            "clientId": "%s",
            "messageId": "%s",
            "endToEndId": "%s",
            "status": "%s",
            "reasonCode": "%s",
            "reasonDesc": "%s",
            "timestamp": "%s"
          }
          """.formatted(clientId, msgId, endToEndId, status, reasonCode, reasonDesc, Instant.now().toString()).trim();

      OutboxEventEntity outbox = new OutboxEventEntity();
      outbox.setId(UUID.randomUUID());
      outbox.setAggregateType("ISO_PAYMENT");
      outbox.setAggregateId(UUID.randomUUID());
      outbox.setEventType("PAIN_002_DISPATCHED");
      outbox.setDedupeKey(dedupeKey);
      outbox.setPayload(payload);
      outbox.setCreatedAt(Instant.now());
      outbox.setNextAttemptAt(Instant.now());
      outboxEventRepository.save(outbox);
    } catch (Exception ex) {
      log.warn("Failed to create outbox event for B2B webhook: {}", ex.getMessage());
    }
  }

  private String serializeIsoPayload(Pain001Dto pain001) {
    try {
      return Iso20022Engine.toJson(pain001);
    } catch (Exception ex) {
      log.warn("Failed to serialize pain.001 to JSON: {}", ex.getMessage());
      return "";
    }
  }
}
