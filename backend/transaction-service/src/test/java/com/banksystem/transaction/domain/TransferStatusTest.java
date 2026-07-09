package com.banksystem.transaction.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class TransferStatusTest {

  @Test
  void hasSagaTerminalStates() {
    assertTrue(EnumSet.allOf(TransferStatus.class).contains(TransferStatus.COMPLETED));
    assertTrue(EnumSet.allOf(TransferStatus.class).contains(TransferStatus.COMPENSATED));
    assertTrue(EnumSet.allOf(TransferStatus.class).contains(TransferStatus.FAILED));
    assertEquals(6, TransferStatus.values().length);
  }
}
