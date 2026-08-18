package com.banksystem.transaction.application.forensics;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ForensicPromptSanitizer {
  private static final Pattern EMAIL = Pattern.compile(
      "(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}");
  private static final Pattern PHONE_OR_ID = Pattern.compile("(?<![0-9])[0-9]{9,16}(?![0-9])");
  private static final Pattern TOKEN = Pattern.compile(
      "(?i)(bearer\\s+|api[_-]?key\\s*[:=]\\s*|secret\\s*[:=]\\s*)[^\\s,;]+"
  );

  public String sanitize(String prompt) {
    String value = EMAIL.matcher(prompt).replaceAll("[REDACTED_EMAIL]");
    value = PHONE_OR_ID.matcher(value).replaceAll("[REDACTED_IDENTIFIER]");
    return TOKEN.matcher(value).replaceAll("[REDACTED_SECRET]");
  }
}
