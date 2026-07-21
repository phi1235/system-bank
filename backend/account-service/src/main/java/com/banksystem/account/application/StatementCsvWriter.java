package com.banksystem.account.application;

import com.banksystem.account.domain.LedgerEntryEntity;
import com.banksystem.account.domain.LedgerEntryType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Pure CSV formatting for ledger export (no I/O side effects beyond string build). */
final class StatementCsvWriter {

  private static final DateTimeFormatter ISO_INSTANT =
      DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.UTC);
  private static final byte[] UTF8_BOM = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

  private StatementCsvWriter() {}

  static byte[] write(List<LedgerEntryEntity> rows) {
    StringBuilder sb = new StringBuilder(256 + rows.size() * 96);
    sb.append("createdAt,entryType,amount,signedAmount,referenceId,description\n");
    for (LedgerEntryEntity e : rows) {
      BigDecimal signed = e.getAmount();
      if (LedgerEntryType.DEBIT.name().equalsIgnoreCase(e.getEntryType())) {
        signed = e.getAmount().negate();
      }
      sb.append(csv(ISO_INSTANT.format(e.getCreatedAt()))).append(',');
      sb.append(csv(e.getEntryType())).append(',');
      sb.append(csv(e.getAmount() == null ? "" : e.getAmount().toPlainString())).append(',');
      sb.append(csv(signed == null ? "" : signed.toPlainString())).append(',');
      sb.append(csv(e.getReferenceId())).append(',');
      sb.append(csv(e.getDescription())).append('\n');
    }
    byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
    byte[] out = new byte[UTF8_BOM.length + body.length];
    System.arraycopy(UTF8_BOM, 0, out, 0, UTF8_BOM.length);
    System.arraycopy(body, 0, out, UTF8_BOM.length, body.length);
    return out;
  }

  private static String csv(String value) {
    if (value == null) {
      return "";
    }
    boolean needsQuote =
        value.indexOf(',') >= 0
            || value.indexOf('"') >= 0
            || value.indexOf('\n') >= 0
            || value.indexOf('\r') >= 0;
    if (!needsQuote) {
      return value;
    }
    return '"' + value.replace("\"", "\"\"") + '"';
  }
}
