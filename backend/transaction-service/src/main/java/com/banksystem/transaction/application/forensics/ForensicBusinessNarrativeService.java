package com.banksystem.transaction.application.forensics;

import com.banksystem.transaction.api.dto.ForensicCaseDtos.ForensicBusinessNarrativeResponse;
import com.banksystem.transaction.api.dto.ForensicCopilotDtos.CopilotCitationResponse;
import com.banksystem.transaction.api.dto.ForensicDtos.FinancialViolationResponse;
import com.banksystem.transaction.api.dto.ForensicDtos.InvestigationDetailResponse;
import com.banksystem.transaction.api.dto.ForensicDtos.InvestigationItemResponse;
import com.banksystem.transaction.api.dto.ForensicDtos.LedgerJournalEvidenceResponse;
import com.banksystem.transaction.api.dto.ForensicDtos.LedgerPostingEvidenceResponse;
import com.banksystem.transaction.application.forensics.ForensicCopilotClaimValidator.ValidationResult;
import com.banksystem.transaction.domain.forensics.ForensicCaseEntity;
import com.banksystem.transaction.domain.forensics.ForensicCaseRepository;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ForensicBusinessNarrativeService {
  private static final Logger log = LoggerFactory.getLogger(ForensicBusinessNarrativeService.class);

  private final ForensicCaseRepository caseRepository;
  private final ForensicJsonSupport jsonSupport;
  private final ForensicAiProvider aiProvider;
  private final ForensicPromptSanitizer promptSanitizer;
  private final ForensicCopilotClaimValidator claimValidator;
  private final Clock clock;

  public ForensicBusinessNarrativeService(
      ForensicCaseRepository caseRepository,
      ForensicJsonSupport jsonSupport,
      ForensicAiProvider aiProvider,
      ForensicPromptSanitizer promptSanitizer,
      ForensicCopilotClaimValidator claimValidator,
      Clock clock) {
    this.caseRepository = caseRepository;
    this.jsonSupport = jsonSupport;
    this.aiProvider = aiProvider;
    this.promptSanitizer = promptSanitizer;
    this.claimValidator = claimValidator;
    this.clock = clock;
  }

  @Transactional
  public ForensicBusinessNarrativeResponse getOrGenerateNarrative(
      ForensicCaseEntity caseEntity,
      InvestigationDetailResponse investigation) {
    if (caseEntity == null) {
      return null;
    }

    // 1. Read from persistent DB cache if available
    String cachedJson = caseEntity.getNarrativeJson();
    if (cachedJson != null && !cachedJson.isBlank()) {
      ForensicBusinessNarrativeResponse cached = parseNarrative(cachedJson);
      if (cached != null) {
        return cached;
      }
    }

    // 2. Generate new narrative
    ForensicBusinessNarrativeResponse generated = generate(investigation);
    if (generated != null) {
      String serialized = jsonSupport.serialize(Map.of(
          "summary", generated.summary(),
          "impactAnalysis", generated.impactAnalysis(),
          "rootCauseNarrative", generated.rootCauseNarrative(),
          "suggestedRemediationNarrative", generated.suggestedRemediationNarrative(),
          "groundedEvidenceKeys", generated.groundedEvidenceKeys(),
          "generatedBy", generated.generatedBy(),
          "generatedAt", generated.generatedAt().toString()
      ));
      caseEntity.updateNarrative(serialized, clock.instant());
      caseRepository.save(caseEntity);
    }
    return generated;
  }

  public ForensicBusinessNarrativeResponse generate(InvestigationDetailResponse investigation) {
    if (investigation == null) {
      return fallbackGenericNarrative(null, "Không có dữ liệu điều tra khả dụng.");
    }

    List<FinancialViolationResponse> violations = investigation.violations();
    InvestigationItemResponse tx = investigation.transaction();
    String currency = tx != null && tx.currency() != null ? tx.currency() : "VND";

    // 1. Fast-Path: Template-Driven synthesis for single / standard rule violations
    if (violations != null && violations.size() == 1) {
      FinancialViolationResponse violation = violations.get(0);
      ForensicBusinessNarrativeResponse templateResult = matchRuleTemplate(violation, investigation, currency);
      if (templateResult != null) {
        return templateResult;
      }
    }

    // 2. Multi-rule or unmapped rule: Attempt LLM Fallback if AI provider is active
    if (aiProvider != null && aiProvider.health().enabled() && aiProvider.health().configured()) {
      try {
        ForensicBusinessNarrativeResponse aiNarrative = tryGenerateWithAi(investigation, currency);
        if (aiNarrative != null) {
          return aiNarrative;
        }
      } catch (Exception e) {
        log.warn("LLM Narrative generation failed, switching to deterministic composite fallback: {}", e.getMessage());
      }
    }

    // 3. Fallback: Deterministic Composite Summary
    return generateDeterministicComposite(investigation, currency);
  }

  private ForensicBusinessNarrativeResponse matchRuleTemplate(
      FinancialViolationResponse violation,
      InvestigationDetailResponse investigation,
      String currency) {
    String ruleCode = violation.ruleCode();
    Instant now = clock.instant();
    InvestigationItemResponse tx = investigation.transaction();
    String txId = tx != null ? tx.transactionId() : "N/A";
    BigDecimal txAmount = tx != null && tx.amount() != null ? tx.amount() : BigDecimal.ZERO;

    List<String> evidenceKeys = new ArrayList<>();
    evidenceKeys.add("rule:" + ruleCode);
    if (tx != null && tx.transactionId() != null) {
      evidenceKeys.add("tx:" + tx.transactionId());
    }

    return switch (ruleCode) {
      case "INV-JOURNAL-001" -> {
        // Debit total != Credit total
        BigDecimal debitTotal = BigDecimal.ZERO;
        BigDecimal creditTotal = BigDecimal.ZERO;
        if (investigation.ledgerEvidence() != null && investigation.ledgerEvidence().journals() != null) {
          for (LedgerJournalEvidenceResponse journal : investigation.ledgerEvidence().journals()) {
            evidenceKeys.add("journal:" + journal.id());
            for (LedgerPostingEvidenceResponse posting : journal.postings()) {
              if ("DEBIT".equalsIgnoreCase(posting.side())) {
                debitTotal = debitTotal.add(posting.amount());
              } else if ("CREDIT".equalsIgnoreCase(posting.side())) {
                creditTotal = creditTotal.add(posting.amount());
              }
            }
          }
        }
        BigDecimal diff = debitTotal.subtract(creditTotal).abs();
        String debitStr = formatMoney(debitTotal, currency);
        String creditStr = formatMoney(creditTotal, currency);
        String diffStr = formatMoney(diff, currency);

        yield new ForensicBusinessNarrativeResponse(
            "Phát hiện mất cân đối bút toán kép trên giao dịch " + txId + ". Tổng Nợ (" + debitStr
                + ") không khớp Tổng Có (" + creditStr + ").",
            "Chênh lệch số dư nội bảng là " + diffStr + ". Báo cáo tài chính nội bộ có nguy cơ không khớp số dư cuối ngày.",
            "Lỗi phân kỳ trạng thái (State Divergence) giữa hệ thống Transaction Gateway và Ledger Service trong quá trình ghi nhận bút toán thanh toán.",
            "Khuyến nghị nghiệp vụ: Thực hiện bút toán điều chỉnh bổ sung để cân bằng đối ứng số tiền " + diffStr + ".",
            evidenceKeys,
            "TEMPLATE",
            now
        );
      }
      case "INV-BALANCE-001" -> {
        String accountId = tx != null && tx.fromAccountId() != null ? tx.fromAccountId() : "Tài khoản nguồn";
        String amountStr = formatMoney(txAmount, currency);
        yield new ForensicBusinessNarrativeResponse(
            "Phát hiện vi phạm hạn mức/số dư khả dụng trên tài khoản " + accountId + " đối với số tiền " + amountStr + ".",
            "Số tiền phong tỏa hoặc ghi nợ vượt quá số dư thực tế, có nguy cơ gây âm số dư khả dụng.",
            "Xung đột lệnh phong tỏa (Hold Collision) hoặc độ trễ đồng bộ số dư trong giao dịch xử lý song song.",
            "Khuyến nghị nghiệp vụ: Kiểm tra lại các lệnh giữ tiền hiện hành trên tài khoản và giải tỏa các khoản phong tỏa trùng.",
            evidenceKeys,
            "TEMPLATE",
            now
        );
      }
      case "INV-DOUBLE-SPEND-001" -> {
        String amountStr = formatMoney(txAmount, currency);
        yield new ForensicBusinessNarrativeResponse(
            "Phát hiện dấu hiệu thực thi trùng lặp (Double Spend) đối với số tiền " + amountStr + " trên giao dịch " + txId + ".",
            "Khách hàng hoặc đối tác có thể bị trừ tiền hai lần cho cùng một yêu cầu thanh toán.",
            "Xử lý song song đồng thời từ phía Client hoặc sự cố Timeout từ Napas/CoreBank dẫn tới việc gọi lại lệnh không kiểm tra Idempotency.",
            "Khuyến nghị nghiệp vụ: Hoàn trả khoản tiền trừ thừa cho khách hàng và hủy bỏ bút toán trùng lặp thứ hai.",
            evidenceKeys,
            "TEMPLATE",
            now
        );
      }
      case "INV-EVENT-JOURNAL-001" -> {
        String amountStr = formatMoney(txAmount, currency);
        yield new ForensicBusinessNarrativeResponse(
            "Sự kiện tài chính số tiền " + amountStr + " đã hoàn tất nhưng chưa có bút toán Sổ cái tương ứng.",
            "Giao dịch thể hiện thành công trên ứng dụng nhưng số dư thực tế trong Sổ cái chưa được phản ánh.",
            "Outbox worker bị gián đoạn kết nối hoặc trễ xử lý trong luồng đồng bộ sự kiện phân tán.",
            "Khuyến nghị nghiệp vụ: Kích hoạt Outbox reconciliation worker để đẩy lại sự kiện sang Ledger Service.",
            evidenceKeys,
            "TEMPLATE",
            now
        );
      }
      default -> null;
    };
  }

  private ForensicBusinessNarrativeResponse tryGenerateWithAi(
      InvestigationDetailResponse investigation,
      String currency) {
    InvestigationItemResponse tx = investigation.transaction();
    String systemPrompt = "Bạn là Chuyên gia Giám định Tài chính Ngân hàng cấp cao (AI Financial Forensics Auditor). "
        + "Nhiệm vụ của bạn là dịch các vi phạm kỹ thuật và dữ liệu điều tra thành tóm tắt nghiệp vụ 4 phần rõ ràng cho Ban Giám đốc và Kế toán trưởng: "
        + "1. Hiện tượng (Tóm tắt ngắn gọn chuyện gì đã xảy ra), "
        + "2. Tác động tài chính (Số tiền, tài khoản ảnh hưởng), "
        + "3. Nguyên nhân gốc rễ (Dưới góc độ nghiệp vụ/luồng thanh toán), "
        + "4. Khuyến nghị khắc phục. "
        + "Quy tắc bắt buộc: Chỉ sử dụng các số tiền, mã tài khoản và mã giao dịch có trong bằng chứng. Tuyệt đối không bịa số liệu. Định dạng tiền tệ theo: " + currency;

    String evidenceText = promptSanitizer.sanitize(jsonSupport.serialize(investigation));
    String aiResponse = aiProvider.complete(systemPrompt, evidenceText);

    if (aiResponse == null || aiResponse.isBlank()) {
      return null;
    }

    // Validate claims using existing ForensicCopilotClaimValidator
    List<CopilotCitationResponse> citations = new ArrayList<>();
    if (tx != null && tx.transactionId() != null) {
      citations.add(new CopilotCitationResponse("TRANSACTION", tx.transactionId(), "Transaction " + tx.transactionId()));
    }
    if (investigation.violations() != null) {
      investigation.violations().forEach(v -> citations.add(
          new CopilotCitationResponse("VIOLATION", v.ruleCode(), v.ruleCode())));
    }

    ValidationResult validation = claimValidator.validate(aiResponse, investigation, citations);
    if (!validation.valid()) {
      log.warn("AI Narrative rejected by ForensicCopilotClaimValidator: {}. Falling back to deterministic narrative.", validation.reason());
      return null;
    }

    List<String> evidenceKeys = citations.stream().map(CopilotCitationResponse::sourceId).toList();
    Instant now = clock.instant();

    return new ForensicBusinessNarrativeResponse(
        extractSection(aiResponse, "1. Hiện tượng", "Phát hiện bất thường tài chính trên giao dịch."),
        extractSection(aiResponse, "2. Tác động tài chính", "Cần rà soát chênh lệch số dư."),
        extractSection(aiResponse, "3. Nguyên nhân gốc rễ", "Sự cố phân kỳ dữ liệu trong luồng thanh toán."),
        extractSection(aiResponse, "4. Khuyến nghị khắc phục", "Đề xuất kiểm tra và thực hiện đối soát."),
        evidenceKeys,
        "LLM",
        now
    );
  }

  private ForensicBusinessNarrativeResponse generateDeterministicComposite(
      InvestigationDetailResponse investigation,
      String currency) {
    InvestigationItemResponse tx = investigation.transaction();
    List<FinancialViolationResponse> violations = investigation.violations() != null
        ? investigation.violations()
        : List.of();

    String txId = tx != null && tx.transactionId() != null ? tx.transactionId() : "N/A";
    BigDecimal txAmount = tx != null && tx.amount() != null ? tx.amount() : BigDecimal.ZERO;
    String amountStr = formatMoney(txAmount, currency);
    Instant now = clock.instant();

    List<String> evidenceKeys = new ArrayList<>();
    if (tx != null && tx.transactionId() != null) {
      evidenceKeys.add("tx:" + tx.transactionId());
    }
    List<String> ruleCodes = violations.stream()
        .map(FinancialViolationResponse::ruleCode)
        .toList();
    violations.forEach(v -> evidenceKeys.add("rule:" + v.ruleCode()));

    String summary = "Phát hiện " + violations.size() + " vi phạm quy tắc bất biến tài chính ("
        + String.join(", ", ruleCodes) + ") trên giao dịch " + txId + " (Số tiền: " + amountStr + ").";

    String impact = "Mức độ rủi ro: " + (violations.stream().anyMatch(v -> "CRITICAL".equalsIgnoreCase(v.severity())) ? "CAO (CRITICAL)" : "TRUNG BÌNH")
        + ". Giao dịch có dấu hiệu không nhất quán giữa trạng thái thanh toán và số dư Sổ cái.";

    String rootCause = "Hệ thống phát hiện sai lệch dữ liệu qua các bước kiểm tra tự động: "
        + violations.stream().map(FinancialViolationResponse::message).reduce((a, b) -> a + "; " + b).orElse("Không xác định");

    String suggestedRemediation = "Khuyến nghị nghiệp vụ: Người kiểm tra (Checker) xem xét lại chi tiết Causal Graph và thực hiện quy trình phê duyệt/khắc phục tương ứng.";

    return new ForensicBusinessNarrativeResponse(
        summary, impact, rootCause, suggestedRemediation, evidenceKeys, "DETERMINISTIC_FALLBACK", now);
  }

  private ForensicBusinessNarrativeResponse fallbackGenericNarrative(String ruleCode, String note) {
    Instant now = clock.instant();
    return new ForensicBusinessNarrativeResponse(
        "Chưa có tóm tắt nghiệp vụ cho hồ sơ này.",
        "Mức độ ảnh hưởng đang được hệ thống phân tích.",
        note != null ? note : "Đang chờ thu thập đầy đủ bằng chứng điều tra.",
        "Khuyến nghị: Rà soát lại dữ liệu bằng chứng từ giao diện quản trị.",
        ruleCode != null ? List.of("rule:" + ruleCode) : List.of(),
        "FALLBACK",
        now
    );
  }

  private String extractSection(String fullText, String sectionHeader, String defaultVal) {
    if (fullText == null) return defaultVal;
    int idx = fullText.indexOf(sectionHeader);
    if (idx < 0) return fullText.length() > 200 ? fullText.substring(0, 200) : fullText;
    int start = idx + sectionHeader.length();
    int end = fullText.indexOf("\n", start + 20);
    if (end < 0) end = fullText.length();
    String sub = fullText.substring(start, end).replaceAll("^[:\\s-]+", "").trim();
    return sub.isBlank() ? defaultVal : sub;
  }

  private String formatMoney(BigDecimal amount, String currency) {
    if (amount == null) return "0 " + (currency != null ? currency : "VND");
    DecimalFormat formatter = (DecimalFormat) NumberFormat.getInstance(Locale.US);
    formatter.applyPattern("#,##0.##");
    return formatter.format(amount) + " " + (currency != null ? currency : "VND");
  }

  @SuppressWarnings("unchecked")
  private ForensicBusinessNarrativeResponse parseNarrative(String json) {
    if (json == null || json.isBlank()) return null;
    try {
      Object parsed = jsonSupport.deserializeAny(json);
      if (parsed instanceof Map<?, ?> map) {
        String summary = map.get("summary") == null ? null : map.get("summary").toString();
        String impactAnalysis = map.get("impactAnalysis") == null ? null : map.get("impactAnalysis").toString();
        String rootCauseNarrative = map.get("rootCauseNarrative") == null ? null : map.get("rootCauseNarrative").toString();
        String suggestedRemediationNarrative = map.get("suggestedRemediationNarrative") == null ? null : map.get("suggestedRemediationNarrative").toString();
        String generatedBy = map.get("generatedBy") == null ? null : map.get("generatedBy").toString();
        String generatedAtStr = map.get("generatedAt") == null ? null : map.get("generatedAt").toString();
        Instant generatedAt = generatedAtStr == null ? null : Instant.parse(generatedAtStr);
        List<String> evidenceKeys = List.of();
        if (map.get("groundedEvidenceKeys") instanceof List<?> list) {
          evidenceKeys = list.stream().map(Object::toString).toList();
        }
        return new ForensicBusinessNarrativeResponse(
            summary, impactAnalysis, rootCauseNarrative, suggestedRemediationNarrative,
            evidenceKeys, generatedBy, generatedAt);
      }
    } catch (Exception e) {
      log.warn("Failed to parse cached narrative JSON: {}", e.getMessage());
    }
    return null;
  }
}
