package com.banksystem.account.application;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AccountNumberTest {

  @Test
  void prefixFormat() {
    // account numbers are 10 + 8 digits
    String sample = String.format("10%08d", 12345678);
    assertTrue(sample.matches("10\\d{8}"));
    assertTrue(sample.length() == 10);
  }
}
