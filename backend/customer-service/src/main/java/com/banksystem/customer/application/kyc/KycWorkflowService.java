package com.banksystem.customer.application.kyc;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.customer.api.dto.KycDtos.CheckerDecisionRequest;
import com.banksystem.customer.api.dto.KycDtos.KycCaseResponse;
import com.banksystem.customer.api.dto.KycDtos.KycDocumentResponse;
import com.banksystem.customer.api.dto.KycDtos.KycHistoryResponse;
import com.banksystem.customer.application.support.OpsAlertPublisher;
import com.banksystem.customer.application.customer.CustomerNotifyPublisher;
import com.banksystem.customer.domain.customer.CustomerEntity;
import com.banksystem.customer.domain.customer.CustomerRepository;
import com.banksystem.customer.domain.kyc.KycCaseEntity;
import com.banksystem.customer.domain.kyc.KycCaseRepository;
import com.banksystem.customer.domain.kyc.KycDecisionHistoryEntity;
import com.banksystem.customer.domain.kyc.KycDecisionHistoryRepository;
import com.banksystem.customer.domain.kyc.KycDocumentEntity;
import com.banksystem.customer.domain.kyc.KycDocumentRepository;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KycWorkflowService {

  private static final Set<String> DOCUMENT_TYPES = Set.of(
      "NATIONAL_ID_FRONT", "NATIONAL_ID_BACK", "PORTRAIT", "PROOF_OF_ADDRESS");
  private static final Set<String> CONTENT_TYPES = Set.of(
      "image/jpeg", "image/png", "application/pdf");

  private final CustomerRepository customerRepository;
  private final KycCaseRepository caseRepository;
  private final KycDocumentRepository documentRepository;
  private final KycDecisionHistoryRepository historyRepository;
  private final KycObjectStorage objectStorage;
  private final MalwareScanner malwareScanner;
  private final OpsAlertPublisher opsAlertPublisher;
  private final CustomerNotifyPublisher customerNotifyPublisher;
  private final long maxFileBytes;

  public KycWorkflowService(
      CustomerRepository customerRepository,
      KycCaseRepository caseRepository,
      KycDocumentRepository documentRepository,
      KycDecisionHistoryRepository historyRepository,
      KycObjectStorage objectStorage,
      MalwareScanner malwareScanner,
      OpsAlertPublisher opsAlertPublisher,
      CustomerNotifyPublisher customerNotifyPublisher,
      @Value("${bank.kyc.max-file-bytes}") long maxFileBytes) {
    this.customerRepository = customerRepository;
    this.caseRepository = caseRepository;
    this.documentRepository = documentRepository;
    this.historyRepository = historyRepository;
    this.objectStorage = objectStorage;
    this.malwareScanner = malwareScanner;
    this.opsAlertPublisher = opsAlertPublisher;
    this.customerNotifyPublisher = customerNotifyPublisher;
    this.maxFileBytes = maxFileBytes;
  }

  @Transactional
  public KycCaseResponse upload(UUID customerId, String documentType, MultipartFile file) {
    requireCustomer(customerId);
    String normalizedType = normalizeDocumentType(documentType);
    validateFile(file);
    KycCaseEntity kycCase = startNewCaseAfterRejection(getOrCreateCase(customerId), customerId);
    if (!"DRAFT".equals(kycCase.getStatus())) {
      throw new BusinessException("KYC_CASE_LOCKED", "Documents cannot be changed after submission");
    }

    validateContentSignature(file);
    scan(file);
    UUID documentId = UUID.randomUUID();
    String objectKey = "kyc/" + customerId + "/" + kycCase.getId() + "/" + documentId;
    String contentType = file.getContentType();
    store(objectKey, file, contentType);

    try {
      KycDocumentEntity document = new KycDocumentEntity();
      document.setId(documentId);
      document.setCaseId(kycCase.getId());
      document.setCustomerId(customerId);
      document.setDocumentType(normalizedType);
      document.setObjectKey(objectKey);
      document.setOriginalName(safeFileName(file.getOriginalFilename()));
      document.setContentType(contentType);
      document.setSizeBytes(file.getSize());
      document.setSha256(sha256(file));
      document.setScanStatus("CLEAN");
      document.setUploadedAt(Instant.now());
      documentRepository.save(document);
      history(kycCase, customerId, "DOCUMENT_UPLOADED", "DRAFT", normalizedType);
      return response(kycCase);
    } catch (RuntimeException ex) {
      objectStorage.delete(objectKey);
      throw ex;
    }
  }

  @Transactional
  public KycCaseResponse submit(UUID customerId) {
    KycCaseEntity kycCase = requireCaseByCustomer(customerId);
    if (!"DRAFT".equals(kycCase.getStatus())) {
      throw new BusinessException("INVALID_KYC_STATE", "Only a draft KYC case can be submitted");
    }
    Set<String> uploadedTypes = documentRepository.findByCaseIdOrderByUploadedAtAsc(kycCase.getId())
        .stream().map(KycDocumentEntity::getDocumentType).collect(Collectors.toSet());
    if (!uploadedTypes.contains("NATIONAL_ID_FRONT")
        || !uploadedTypes.contains("NATIONAL_ID_BACK")
        || !uploadedTypes.contains("PORTRAIT")) {
      throw new BusinessException(
          "KYC_DOCUMENTS_INCOMPLETE", "Identity front, back and portrait are required");
    }
    String previous = kycCase.getStatus();
    Instant submittedAt = Instant.now();
    kycCase.setStatus("PENDING_APPROVAL");
    kycCase.setSubmittedAt(submittedAt);
    kycCase.setMakerId(customerId);
    kycCase.setMakerRecommendation("SUBMIT");
    kycCase.setMakerAt(submittedAt);
    kycCase.setUpdatedAt(submittedAt);
    caseRepository.save(kycCase);
    CustomerEntity customer = requireCustomer(customerId);
    customer.setKycStatus("PENDING");
    customer.setUpdatedAt(Instant.now());
    customerRepository.save(customer);
    history(kycCase, customerId, "SUBMITTED", previous, null);
    return response(kycCase);
  }

  @Transactional(readOnly = true)
  public KycCaseResponse getByCustomer(UUID customerId) {
    return response(requireCaseByCustomer(customerId));
  }

  @Transactional
  public KycCaseResponse checkerDecision(
      UUID caseId, UUID checkerId, CheckerDecisionRequest request) {
    KycCaseEntity kycCase = requireCase(caseId);
    if (!"PENDING_APPROVAL".equals(kycCase.getStatus())
        && !"SUBMITTED".equals(kycCase.getStatus())) {
      throw new BusinessException("INVALID_KYC_STATE", "KYC case is not awaiting checker decision");
    }
    // The customer who created/submitted the case is always the maker. Normalize
    // legacy cases that previously stored a back-office reviewer as maker.
    kycCase.setMakerId(kycCase.getCustomerId());
    kycCase.setMakerRecommendation("SUBMIT");
    kycCase.setMakerNote(null);
    kycCase.setMakerAt(kycCase.getSubmittedAt());
    if (checkerId.equals(kycCase.getMakerId())) {
      throw new BusinessException("MAKER_CHECKER_CONFLICT", "Maker and checker must be different users");
    }
    String decision = normalizeDecision(request.decision());
    String previous = kycCase.getStatus();
    String finalCaseStatus = "APPROVE".equals(decision) ? "APPROVED" : "REJECTED";
    kycCase.setCheckerId(checkerId);
    kycCase.setDecision(decision);
    kycCase.setDecisionReason(trim(request.reason()));
    kycCase.setDecidedAt(Instant.now());
    kycCase.setStatus(finalCaseStatus);
    kycCase.setUpdatedAt(Instant.now());
    caseRepository.save(kycCase);

    CustomerEntity customer = requireCustomer(kycCase.getCustomerId());
    String previousCustomerStatus = customer.getKycStatus();
    customer.setKycStatus("APPROVE".equals(decision) ? "VERIFIED" : "REJECTED");
    customer.setUpdatedAt(Instant.now());
    customerRepository.save(customer);
    history(kycCase, checkerId, "CHECKER_DECIDED", previous,
        decision + noteSuffix(request.reason()));
    customerNotifyPublisher.kycDecision(
        customer, kycCase.getId(), "APPROVE".equals(decision), request.reason());
    opsAlertPublisher.kycUpdated(customer, previousCustomerStatus);
    return response(kycCase);
  }

  @Transactional(readOnly = true)
  public DocumentDownload download(UUID documentId, UUID customerId, boolean staff) {
    KycDocumentEntity document = documentRepository.findById(documentId)
        .orElseThrow(() -> new BusinessException("KYC_DOCUMENT_NOT_FOUND", "KYC document not found"));
    if (!staff && !document.getCustomerId().equals(customerId)) {
      throw new BusinessException("FORBIDDEN", "KYC document does not belong to the customer");
    }
    return new DocumentDownload(
        document.getOriginalName(), document.getContentType(), objectStorage.get(document.getObjectKey()));
  }

  private KycCaseEntity getOrCreateCase(UUID customerId) {
    return caseRepository.findByCustomerIdAndCurrentTrue(customerId).orElseGet(() -> {
      Instant now = Instant.now();
      KycCaseEntity created = new KycCaseEntity();
      created.setId(UUID.randomUUID());
      created.setCustomerId(customerId);
      created.setStatus("DRAFT");
      created.setCurrent(true);
      created.setCreatedAt(now);
      created.setUpdatedAt(now);
      return caseRepository.save(created);
    });
  }

  private KycCaseEntity startNewCaseAfterRejection(KycCaseEntity kycCase, UUID actorId) {
    if (!"REJECTED".equals(kycCase.getStatus())) {
      return kycCase;
    }
    kycCase.setCurrent(false);
    kycCase.setUpdatedAt(Instant.now());
    caseRepository.saveAndFlush(kycCase);

    Instant now = Instant.now();
    KycCaseEntity created = new KycCaseEntity();
    created.setId(UUID.randomUUID());
    created.setCustomerId(kycCase.getCustomerId());
    created.setStatus("DRAFT");
    created.setCurrent(true);
    created.setCreatedAt(now);
    created.setUpdatedAt(now);
    caseRepository.save(created);
    history(kycCase, actorId, "SUPERSEDED", "REJECTED", created.getId().toString());
    history(created, actorId, "NEW_CASE_CREATED", null, kycCase.getId().toString());
    return created;
  }

  private void history(KycCaseEntity kycCase, UUID actorId, String action, String from, String note) {
    KycDecisionHistoryEntity item = new KycDecisionHistoryEntity();
    item.setId(UUID.randomUUID());
    item.setCaseId(kycCase.getId());
    item.setActorId(actorId);
    item.setAction(action);
    item.setFromStatus(from);
    item.setToStatus(kycCase.getStatus());
    item.setNote(trim(note));
    item.setCreatedAt(Instant.now());
    historyRepository.save(item);
  }

  private KycCaseResponse response(KycCaseEntity kycCase) {
    List<KycDocumentResponse> documents = documentRepository
        .findByCaseIdOrderByUploadedAtAsc(kycCase.getId()).stream().map(this::documentResponse).toList();
    List<KycHistoryResponse> history = historyRepository
        .findByCaseIdOrderByCreatedAtAsc(kycCase.getId()).stream().map(this::historyResponse).toList();
    return new KycCaseResponse(
        kycCase.getId().toString(), kycCase.getCustomerId().toString(), kycCase.getStatus(),
        id(kycCase.getMakerId()), kycCase.getMakerRecommendation(), kycCase.getMakerNote(),
        kycCase.getMakerAt(), id(kycCase.getCheckerId()), kycCase.getDecision(),
        kycCase.getDecisionReason(), kycCase.getSubmittedAt(), kycCase.getDecidedAt(),
        documents, history, kycCase.getCreatedAt(), kycCase.getUpdatedAt());
  }

  private KycDocumentResponse documentResponse(KycDocumentEntity item) {
    return new KycDocumentResponse(
        item.getId().toString(), item.getDocumentType(), item.getOriginalName(),
        item.getContentType(), item.getSizeBytes(), item.getSha256(), item.getScanStatus(),
        item.getUploadedAt());
  }

  private KycHistoryResponse historyResponse(KycDecisionHistoryEntity item) {
    return new KycHistoryResponse(
        item.getId().toString(), item.getActorId().toString(), item.getAction(),
        item.getFromStatus(), item.getToStatus(), item.getNote(), item.getCreatedAt());
  }

  private CustomerEntity requireCustomer(UUID id) {
    return customerRepository.findById(id)
        .orElseThrow(() -> new BusinessException("CUSTOMER_NOT_FOUND", "Customer not found"));
  }

  private KycCaseEntity requireCase(UUID id) {
    return caseRepository.findById(id)
        .orElseThrow(() -> new BusinessException("KYC_CASE_NOT_FOUND", "KYC case not found"));
  }

  private KycCaseEntity requireCaseByCustomer(UUID customerId) {
    return caseRepository.findByCustomerIdAndCurrentTrue(customerId)
        .orElseThrow(() -> new BusinessException("KYC_CASE_NOT_FOUND", "KYC case not found"));
  }

  private void validateFile(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new BusinessException("KYC_FILE_REQUIRED", "KYC document file is required");
    }
    if (file.getSize() > maxFileBytes) {
      throw new BusinessException("KYC_FILE_TOO_LARGE", "KYC document exceeds the configured size limit");
    }
    if (!CONTENT_TYPES.contains(file.getContentType())) {
      throw new BusinessException("KYC_FILE_TYPE_INVALID", "Only JPEG, PNG and PDF are accepted");
    }
  }

  private String normalizeDocumentType(String input) {
    String value = input == null ? "" : input.trim().toUpperCase();
    if (!DOCUMENT_TYPES.contains(value)) {
      throw new BusinessException("KYC_DOCUMENT_TYPE_INVALID", "Invalid KYC document type");
    }
    return value;
  }

  private String normalizeDecision(String input) {
    String value = input == null ? "" : input.trim().toUpperCase();
    if (!Set.of("APPROVE", "REJECT").contains(value)) {
      throw new BusinessException("KYC_DECISION_INVALID", "Decision must be APPROVE or REJECT");
    }
    return value;
  }

  private void scan(MultipartFile file) {
    try (InputStream content = file.getInputStream()) {
      malwareScanner.assertClean(content);
    } catch (Exception ex) {
      if (ex instanceof BusinessException businessException) {
        throw businessException;
      }
      throw new BusinessException("KYC_FILE_READ_FAILED", "Cannot read KYC document");
    }
  }

  private void store(String objectKey, MultipartFile file, String contentType) {
    try (InputStream content = file.getInputStream()) {
      objectStorage.put(objectKey, content, file.getSize(), contentType);
    } catch (Exception ex) {
      if (ex instanceof BusinessException businessException) {
        throw businessException;
      }
      throw new BusinessException("KYC_FILE_STORE_FAILED", "Cannot store KYC document");
    }
  }

  private void validateContentSignature(MultipartFile file) {
    try (InputStream content = file.getInputStream()) {
      byte[] header = content.readNBytes(8);
      boolean valid = switch (file.getContentType()) {
        case "image/jpeg" -> header.length >= 3
            && (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8
            && (header[2] & 0xff) == 0xff;
        case "image/png" -> header.length >= 8
            && (header[0] & 0xff) == 0x89 && header[1] == 0x50 && header[2] == 0x4e
            && header[3] == 0x47 && header[4] == 0x0d && header[5] == 0x0a
            && header[6] == 0x1a && header[7] == 0x0a;
        case "application/pdf" -> header.length >= 5 && header[0] == 0x25 && header[1] == 0x50
            && header[2] == 0x44 && header[3] == 0x46 && header[4] == 0x2d;
        default -> false;
      };
      if (!valid) {
        throw new BusinessException(
            "KYC_FILE_SIGNATURE_INVALID", "File content does not match its declared type");
      }
    } catch (BusinessException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new BusinessException("KYC_FILE_READ_FAILED", "Cannot read KYC document");
    }
  }

  private String sha256(MultipartFile file) {
    try (InputStream content = file.getInputStream()) {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] buffer = new byte[8192];
      int count;
      while ((count = content.read(buffer)) >= 0) {
        digest.update(buffer, 0, count);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (Exception ex) {
      throw new BusinessException("KYC_FILE_READ_FAILED", "Cannot hash KYC document");
    }
  }

  private String safeFileName(String value) {
    if (value == null || value.isBlank()) {
      return "document";
    }
    String normalized = value.replace('\\', '/');
    String name = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
    return name.length() <= 255 ? name : name.substring(name.length() - 255);
  }

  private static String trim(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500);
  }

  private static String noteSuffix(String note) {
    return note == null || note.isBlank() ? "" : ": " + note.trim();
  }

  private static String id(UUID id) {
    return id == null ? null : id.toString();
  }

  public record DocumentDownload(
      String fileName, String contentType, KycObjectStorage.StoredObject object) {}
}
