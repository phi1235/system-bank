package com.banksystem.account.application.card;

import java.security.SecureRandom;

/**
 * Virtual debit card PAN: domestic BIN prefix + random body + Luhn check digit (16 digits).
 * Pure functions over a caller-supplied random source for testability.
 */
public final class CardNumberGenerator {

  /** Demo NAPAS-style BIN; not a real issuer BIN. */
  static final String BIN = "970459";
  static final int PAN_LENGTH = 16;

  private static final SecureRandom RANDOM = new SecureRandom();

  private CardNumberGenerator() {}

  public static String generate() {
    StringBuilder pan = new StringBuilder(BIN);
    while (pan.length() < PAN_LENGTH - 1) {
      pan.append(RANDOM.nextInt(10));
    }
    pan.append(luhnCheckDigit(pan.toString()));
    return pan.toString();
  }

  /** Check digit making {@code partial + digit} pass the Luhn algorithm. */
  static int luhnCheckDigit(String partial) {
    int sum = 0;
    boolean doubleIt = true;
    for (int i = partial.length() - 1; i >= 0; i--) {
      int d = partial.charAt(i) - '0';
      if (doubleIt) {
        d *= 2;
        if (d > 9) {
          d -= 9;
        }
      }
      sum += d;
      doubleIt = !doubleIt;
    }
    return (10 - (sum % 10)) % 10;
  }

  public static boolean isLuhnValid(String pan) {
    if (pan == null || !pan.matches("\\d{12,19}")) {
      return false;
    }
    int sum = 0;
    boolean doubleIt = false;
    for (int i = pan.length() - 1; i >= 0; i--) {
      int d = pan.charAt(i) - '0';
      if (doubleIt) {
        d *= 2;
        if (d > 9) {
          d -= 9;
        }
      }
      sum += d;
      doubleIt = !doubleIt;
    }
    return sum % 10 == 0;
  }
}
