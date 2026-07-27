package com.banksystem.account.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class CardNumberGeneratorTest {

  @RepeatedTest(20)
  void generatedPanIsSixteenDigitsWithBinAndValidLuhn() {
    String pan = CardNumberGenerator.generate();

    assertEquals(16, pan.length());
    assertTrue(pan.startsWith(CardNumberGenerator.BIN));
    assertTrue(CardNumberGenerator.isLuhnValid(pan));
  }

  @Test
  void luhnCheckDigitMatchesKnownExample() {
    // Classic Luhn example: 7992739871 + check digit 3
    assertEquals(3, CardNumberGenerator.luhnCheckDigit("7992739871"));
    // Full 16-digit round trip: partial + computed digit must validate
    String partial = "970459123456789";
    assertTrue(
        CardNumberGenerator.isLuhnValid(partial + CardNumberGenerator.luhnCheckDigit(partial)));
  }

  @Test
  void luhnRejectsTamperedPan() {
    String pan = CardNumberGenerator.generate();
    char last = pan.charAt(15);
    String tampered = pan.substring(0, 15) + (last == '9' ? '0' : (char) (last + 1));
    assertFalse(CardNumberGenerator.isLuhnValid(tampered));
    assertFalse(CardNumberGenerator.isLuhnValid("abcd"));
  }
}
