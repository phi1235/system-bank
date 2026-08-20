package com.banksystem.corporate.application.receipt;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.corporate.api.dto.PayoutBatchDtos.ReceiptArtifactResponse;
import com.banksystem.corporate.application.corporation.CorporateAuthorizationService;
import com.banksystem.corporate.domain.payout.PayoutBatchEntity;
import com.banksystem.corporate.domain.payout.PayoutItemEntity;
import com.banksystem.corporate.domain.receipt.ReceiptArtifactEntity;
import com.banksystem.corporate.domain.receipt.ReceiptArtifactRepository;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ReceiptService {

  private static final Logger log = LoggerFactory.getLogger(ReceiptService.class);
  private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
  private static final DecimalFormat MONEY_FMT = new DecimalFormat("#,###");

  private final ReceiptArtifactRepository receiptRepository;
  private final MinioClient minioClient;
  private final CorporateAuthorizationService authorizationService;
  private final String bucketName;

  public ReceiptService(
      ReceiptArtifactRepository receiptRepository,
      MinioClient minioClient,
      CorporateAuthorizationService authorizationService,
      @Value("${bank.storage.bucket:bank-corporate}") String bucketName) {
    this.receiptRepository = receiptRepository;
    this.minioClient = minioClient;
    this.authorizationService = authorizationService;
    this.bucketName = bucketName;
  }

  public List<ReceiptArtifactResponse> listBatchReceipts(UUID corporateId, UUID batchId, UUID userId) {
    authorizationService.requireActiveMember(corporateId, userId);
    return receiptRepository.findByCorporateIdAndBatchId(corporateId, batchId).stream()
        .map(this::toResponse)
        .toList();
  }

  public byte[] downloadReceipt(UUID corporateId, UUID artifactId, UUID userId) {
    authorizationService.requireActiveMember(corporateId, userId);
    ReceiptArtifactEntity artifact = receiptRepository.findByCorporateIdAndId(corporateId, artifactId)
        .orElseThrow(() -> new BusinessException("ARTIFACT_NOT_FOUND", "Receipt artifact not found or does not belong to this corporation"));
    return downloadArtifact(artifact);
  }

  public ReceiptArtifactResponse toResponse(ReceiptArtifactEntity artifact) {
    return new ReceiptArtifactResponse(
        artifact.getId(),
        artifact.getCorporateId(),
        artifact.getBatchId(),
        artifact.getItemId(),
        artifact.getArtifactType(),
        artifact.getFileKey(),
        artifact.getFileSha256(),
        artifact.getFileSizeBytes(),
        artifact.isEmailSent(),
        artifact.getEmailSentAt(),
        artifact.getCreatedAt());
  }

  public ReceiptArtifactEntity generateItemReceipt(PayoutBatchEntity batch, PayoutItemEntity item) {
    var existing = receiptRepository.findByItemId(item.getId());
    if (existing.isPresent()) {
      return existing.get();
    }
    byte[] pdfBytes = renderItemReceiptPdf(batch, item);
    String sha256 = computeSha256(pdfBytes);
    String fileKey = "corporates/" + batch.getCorporateId() + "/batches/" + batch.getId() + "/receipts/item-" + item.getId() + ".pdf";

    uploadToMinio(fileKey, pdfBytes, "application/pdf");

    ReceiptArtifactEntity artifact = new ReceiptArtifactEntity();
    artifact.setId(deterministicId("ITEM_RECEIPT", item.getId()));
    artifact.setCorporateId(batch.getCorporateId());
    artifact.setBatchId(batch.getId());
    artifact.setItemId(item.getId());
    artifact.setArtifactType("INDIVIDUAL_PAYOUT_RECEIPT");
    artifact.setFileKey(fileKey);
    artifact.setFileSha256(sha256);
    artifact.setFileSizeBytes(pdfBytes.length);
    artifact.setEmailSent(false);
    String recipient = item.getEmployeeEmail();
    if (recipient == null || recipient.isBlank()) {
      artifact.setEmailStatus("NOT_REQUIRED");
    } else {
      artifact.setEmailRecipient(recipient.trim());
      artifact.setEmailStatus("PENDING");
      artifact.setEmailNextAttemptAt(Instant.now());
    }
    artifact.setCreatedAt(Instant.now());
    return receiptRepository.save(artifact);
  }

  public ReceiptArtifactEntity generateBatchConsolidatedReport(PayoutBatchEntity batch, List<PayoutItemEntity> items) {
    var existing = receiptRepository.findByBatchIdAndArtifactType(batch.getId(), "CONSOLIDATED_BATCH_REPORT");
    if (existing.isPresent()) {
      return existing.get();
    }
    byte[] pdfBytes = renderBatchSummaryPdf(batch, items);
    String sha256 = computeSha256(pdfBytes);
    String fileKey = "corporates/" + batch.getCorporateId() + "/batches/" + batch.getId() + "/consolidated-report.pdf";

    uploadToMinio(fileKey, pdfBytes, "application/pdf");

    ReceiptArtifactEntity artifact = new ReceiptArtifactEntity();
    artifact.setId(deterministicId("BATCH_REPORT", batch.getId()));
    artifact.setCorporateId(batch.getCorporateId());
    artifact.setBatchId(batch.getId());
    artifact.setItemId(null);
    artifact.setArtifactType("CONSOLIDATED_BATCH_REPORT");
    artifact.setFileKey(fileKey);
    artifact.setFileSha256(sha256);
    artifact.setFileSizeBytes(pdfBytes.length);
    artifact.setEmailSent(false);
    artifact.setCreatedAt(Instant.now());

    return receiptRepository.save(artifact);
  }

  public byte[] downloadArtifact(ReceiptArtifactEntity artifact) {
    try (InputStream is = minioClient.getObject(
        GetObjectArgs.builder().bucket(bucketName).object(artifact.getFileKey()).build())) {
      return is.readAllBytes();
    } catch (Exception e) {
      log.error("Failed to download artifact from MinIO: {}", artifact.getFileKey(), e);
      throw new BusinessException("DOWNLOAD_FAILED", "Failed to retrieve artifact file: " + e.getMessage());
    }
  }

  private byte[] renderItemReceiptPdf(PayoutBatchEntity batch, PayoutItemEntity item) {
    try (PDDocument doc = new PDDocument();
         ByteArrayOutputStream out = new ByteArrayOutputStream()) {

      PDPage page = new PDPage();
      doc.addPage(page);

      try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 16);
        cs.newLineAtOffset(50, 750);
        cs.showText("BANK SYSTEM - BIEN LAI CHI TRA");

        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
        cs.newLineAtOffset(0, -30);
        cs.showText("Ma giao dich: " + (item.getTransactionId() != null ? item.getTransactionId().toString() : "N/A"));
        cs.newLineAtOffset(0, -20);
        cs.showText("Ngay thuc hien: " + TIME_FMT.format(Instant.now()));
        cs.newLineAtOffset(0, -20);
        cs.showText("Lo chi tra: " + batch.getBatchName());
        cs.newLineAtOffset(0, -20);
        cs.showText("Tai khoan nguon: " + batch.getSourceAccountNumber());
        cs.newLineAtOffset(0, -20);
        cs.showText("Nguoi nhan: " + item.getBeneficiaryName() + " (" + (item.getEmployeeCode() != null ? item.getEmployeeCode() : "") + ")");
        cs.newLineAtOffset(0, -20);
        cs.showText("So tai khoan nhan: " + item.getAccountNumber() + " - Ngan hang: " + item.getBankCode());
        cs.newLineAtOffset(0, -20);
        cs.showText("So tien: " + MONEY_FMT.format(item.getAmount()) + " " + item.getCurrency());
        cs.newLineAtOffset(0, -20);
        cs.showText("Noi dung: " + (item.getDescription() != null ? item.getDescription() : ""));
        cs.newLineAtOffset(0, -20);
        cs.showText("Trang thai: THANH CONG (SUCCESS)");
        cs.endText();
      }

      doc.save(out);
      return out.toByteArray();
    } catch (Exception e) {
      log.error("Failed to render item receipt PDF", e);
      throw new BusinessException("PDF_GENERATION_FAILED", "Failed to render receipt PDF: " + e.getMessage());
    }
  }

  private byte[] renderBatchSummaryPdf(PayoutBatchEntity batch, List<PayoutItemEntity> items) {
    try (PDDocument doc = new PDDocument();
         ByteArrayOutputStream out = new ByteArrayOutputStream()) {

      PDPage page = new PDPage();
      doc.addPage(page);

      try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 16);
        cs.newLineAtOffset(50, 750);
        cs.showText("BANK SYSTEM - BAO CAO TONG HOP CHI TRA LO");

        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
        cs.newLineAtOffset(0, -30);
        cs.showText("Ten lo: " + batch.getBatchName());
        cs.newLineAtOffset(0, -20);
        cs.showText("Ma lo (ID): " + batch.getId());
        cs.newLineAtOffset(0, -20);
        cs.showText("Tai khoan nguon: " + batch.getSourceAccountNumber());
        cs.newLineAtOffset(0, -20);
        cs.showText("Tong so mon: " + batch.getTotalItems() + " | Thanh cong: " + batch.getSuccessfulItems() + " | That bai: " + batch.getFailedItems());
        cs.newLineAtOffset(0, -20);
        cs.showText("Tong so tien: " + MONEY_FMT.format(batch.getTotalAmount()) + " " + batch.getCurrency());
        cs.newLineAtOffset(0, -20);
        cs.showText("Trang thai lo: " + batch.getStatus());
        cs.newLineAtOffset(0, -20);
        cs.showText("Thoi gian hoan tat: " + TIME_FMT.format(batch.getCompletedAt() != null ? batch.getCompletedAt() : Instant.now()));
        cs.endText();
      }

      doc.save(out);
      return out.toByteArray();
    } catch (Exception e) {
      log.error("Failed to render batch summary PDF", e);
      throw new BusinessException("PDF_GENERATION_FAILED", "Failed to render consolidated report PDF: " + e.getMessage());
    }
  }

  private void uploadToMinio(String key, byte[] data, String contentType) {
    try {
      minioClient.putObject(
          PutObjectArgs.builder()
              .bucket(bucketName)
              .object(key)
              .stream(new ByteArrayInputStream(data), data.length, -1)
              .contentType(contentType)
              .build());
    } catch (Exception e) {
      log.error("Could not upload PDF receipt to MinIO bucket [{}], key [{}]: {}", bucketName, key, e.getMessage());
      throw new BusinessException("STORAGE_UPLOAD_FAILED", "Failed to upload receipt artifact to MinIO: " + e.getMessage());
    }
  }

  private String computeSha256(byte[] bytes) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(bytes);
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  private UUID deterministicId(String artifactType, UUID sourceId) {
    return UUID.nameUUIDFromBytes(
        (artifactType + ":" + sourceId).getBytes(StandardCharsets.UTF_8));
  }
}
