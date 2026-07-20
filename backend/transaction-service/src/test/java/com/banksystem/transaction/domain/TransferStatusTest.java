package com.banksystem.transaction.domain;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TransferStatusTest {

  @Test
  void valuesCoverSagaLifecycle() {
    assertArrayEquals(
        new TransferStatus[] {
          TransferStatus.PENDING,
          TransferStatus.DEBITED,
          TransferStatus.COMPLETED,
          TransferStatus.FAILED,
          TransferStatus.COMPENSATING,
          TransferStatus.COMPENSATED
        },
        TransferStatus.values());
  }

  @Test
  void valueOfIsCaseSensitive() {
    assertEquals(TransferStatus.COMPLETED, TransferStatus.valueOf("COMPLETED"));
    assertThrows(IllegalArgumentException.class, () -> TransferStatus.valueOf("completed"));
  }
}
