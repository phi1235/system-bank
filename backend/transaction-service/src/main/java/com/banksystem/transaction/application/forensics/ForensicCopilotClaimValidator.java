package com.banksystem.transaction.application.forensics;

import com.banksystem.transaction.api.dto.ForensicCopilotDtos.CopilotCitationResponse;
import com.banksystem.transaction.api.dto.ForensicDtos.InvestigationDetailResponse;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ForensicCopilotClaimValidator {
  private static final Pattern UUID_PATTERN = Pattern.compile(
      "(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b");
  private static final Pattern MONEY_PATTERN = Pattern.compile(
      "(?i)\\b([0-9][0-9.,]*)\\s*(VND|USD|EUR|JPY|GBP)\\b");

  public ValidationResult validate(
      String answer,
      InvestigationDetailResponse evidence,
      List<CopilotCitationResponse> citations) {
    Set<String> allowedIds = new HashSet<>();
    citations.forEach(citation -> allowedIds.add(citation.sourceId().toLowerCase()));
    if (evidence.transaction().fromAccountId() != null) {
      allowedIds.add(evidence.transaction().fromAccountId().toLowerCase());
    }
    if (evidence.transaction().toAccountId() != null) {
      allowedIds.add(evidence.transaction().toAccountId().toLowerCase());
    }
    Matcher idMatcher = UUID_PATTERN.matcher(answer);
    while (idMatcher.find()) {
      if (!allowedIds.contains(idMatcher.group().toLowerCase())) {
        return new ValidationResult(false, "UNSUPPORTED_EVIDENCE_ID");
      }
    }

    Set<String> allowedAmounts = new HashSet<>();
    addAmount(allowedAmounts, evidence.transaction().amount());
    addAmount(allowedAmounts, evidence.transaction().feeAmount());
    evidence.ledgerEvidence().journals().forEach(journal -> journal.postings()
        .forEach(posting -> addAmount(allowedAmounts, posting.amount())));
    evidence.ledgerEvidence().holds().forEach(hold -> addAmount(allowedAmounts, hold.amount()));
    Matcher moneyMatcher = MONEY_PATTERN.matcher(answer);
    while (moneyMatcher.find()) {
      String digits = moneyMatcher.group(1).replaceAll("[^0-9]", "").replaceFirst("^0+(?!$)", "");
      if (!allowedAmounts.contains(digits)) {
        return new ValidationResult(false, "UNSUPPORTED_MONETARY_CLAIM");
      }
    }
    if (!citations.isEmpty() && !containsCitation(answer, citations)) {
      return new ValidationResult(false, "MISSING_DURABLE_CITATION");
    }
    return new ValidationResult(true, "GROUNDED");
  }

  private boolean containsCitation(String answer, List<CopilotCitationResponse> citations) {
    String normalized = answer.toLowerCase();
    return citations.stream().anyMatch(citation -> normalized.contains(citation.sourceId().toLowerCase()));
  }

  private void addAmount(Set<String> values, BigDecimal amount) {
    if (amount == null) return;
    values.add(digits(amount.stripTrailingZeros().toPlainString()));
    values.add(digits(amount.setScale(2).toPlainString()));
  }

  private String digits(String value) {
    return value.replaceAll("[^0-9]", "").replaceFirst("^0+(?!$)", "");
  }

  public record ValidationResult(boolean valid, String reason) {}
}
