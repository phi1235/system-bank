package com.banksystem.transaction.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.banksystem.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class TransferSagaReasonFormatTest {

  @Test
  void formatsBusinessExceptionAsCodeMessage() {
    BusinessException ex = new BusinessException(
        "INSUFFICIENT_BALANCE", "Account balance is insufficient", HttpStatus.UNPROCESSABLE_ENTITY);
    assertEquals(
        "INSUFFICIENT_BALANCE: Account balance is insufficient",
        TransferSagaOrchestrator.formatReason(ex));
  }

  @Test
  void formatsGenericExceptionWithFallbackCode() {
    assertEquals(
        "TRANSFER_FAILED: boom",
        TransferSagaOrchestrator.formatReason(new RuntimeException("boom")));
  }

  @Test
  void doesNotDoublePrefix() {
    assertEquals(
        "FEE_GL_FAILED: already",
        TransferSagaOrchestrator.formatReason("FEE_GL_FAILED", "FEE_GL_FAILED: already"));
  }

  @Test
  void codeOnlyWhenMessageBlank() {
    assertEquals("DEBIT_ERROR", TransferSagaOrchestrator.formatReason("DEBIT_ERROR", "  "));
  }
}
