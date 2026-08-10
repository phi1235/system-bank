package com.banksystem.notification.application.notification.impl;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Consistent human-readable money formatting for notification text. */
final class NotificationMoneyFormatter {

  private static final Pattern AMOUNT_BEFORE_CURRENCY = Pattern.compile(
      "(?<![\\p{L}\\p{N}_.])([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?)"
          + "(?=\\s+[A-Z]{3}\\b)");

  private NotificationMoneyFormatter() {}

  static String format(String rawAmount) {
    if (rawAmount == null || rawAmount.isBlank()) {
      return "";
    }
    try {
      BigDecimal normalized = new BigDecimal(rawAmount.trim()).stripTrailingZeros();
      DecimalFormatSymbols symbols = new DecimalFormatSymbols();
      symbols.setGroupingSeparator('.');
      symbols.setDecimalSeparator(',');
      DecimalFormat format = new DecimalFormat("#,##0", symbols);
      format.setMinimumFractionDigits(0);
      format.setMaximumFractionDigits(Math.min(Math.max(normalized.scale(), 0), 20));
      format.setGroupingUsed(true);
      return format.format(normalized);
    } catch (NumberFormatException ex) {
      return rawAmount.trim();
    }
  }

  /** Normalizes legacy persisted bodies so old scientific/.0 amounts also render correctly. */
  static String normalizeBody(String body) {
    if (body == null || body.isBlank()) {
      return body == null ? "" : body;
    }
    Matcher matcher = AMOUNT_BEFORE_CURRENCY.matcher(body);
    StringBuffer result = new StringBuffer();
    while (matcher.find()) {
      matcher.appendReplacement(result, Matcher.quoteReplacement(format(matcher.group(1))));
    }
    matcher.appendTail(result);
    return result.toString();
  }
}
