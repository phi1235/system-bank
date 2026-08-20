package com.banksystem.corporate.application.payout;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.corporate.api.dto.PayoutBatchDtos.BatchSummaryResponse;
import com.banksystem.corporate.domain.payout.PayoutBatchEntity;
import com.banksystem.corporate.domain.payout.PayoutBatchRepository;
import com.banksystem.corporate.domain.payout.PayoutItemEntity;
import com.banksystem.corporate.domain.payout.PayoutItemRepository;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ExcelIngestionService {

  private static final Logger log = LoggerFactory.getLogger(ExcelIngestionService.class);
  private static final int MAX_ROWS = 10000;
  private static final long MAX_FILE_BYTES = 25L * 1024 * 1024;

  private final PayoutBatchRepository batchRepository;
  private final PayoutItemRepository itemRepository;
  private final ExcelIngestionPersistenceService persistenceService;
  private final MinioClient minioClient;
  private final String bucketName;

  public ExcelIngestionService(
      PayoutBatchRepository batchRepository,
      PayoutItemRepository itemRepository,
      ExcelIngestionPersistenceService persistenceService,
      MinioClient minioClient,
      @Value("${bank.storage.bucket:bank-corporate}") String bucketName) {
    this.batchRepository = batchRepository;
    this.itemRepository = itemRepository;
    this.persistenceService = persistenceService;
    this.minioClient = minioClient;
    this.bucketName = bucketName;
  }

  public record RowParsedData(
      int rowNumber,
      String employeeCode,
      String beneficiaryName,
      String accountNumber,
      String bankCode,
      BigDecimal amount,
      String description,
      String employeeEmail,
      String payrollPeriod,
      boolean valid,
      String errorMessage
  ) {}

  public BatchSummaryResponse ingestExcel(
      UUID corporateId,
      UUID batchId,
      InputStream input,
      String originalFilename) {
    PayoutBatchEntity batch = batchRepository.findByCorporateIdAndId(corporateId, batchId).orElseThrow(() ->
        new BusinessException("BATCH_NOT_FOUND", "Payout batch not found"));

    if (!"DRAFT".equals(batch.getStatus()) && !"RETURNED".equals(batch.getStatus()) && !"VALIDATION_FAILED".equals(batch.getStatus())) {
      throw new BusinessException("INVALID_BATCH_STATE", "Batch cannot accept file upload in state: " + batch.getStatus());
    }

    Path tempFile = null;
    try {
      tempFile = Files.createTempFile("corporate-payout-", ".xlsx");
      String sha256 = spoolAndHash(input, tempFile);

    // Deduplication check
      var existingWithSameHash = batchRepository.findByCorporateIdAndSourceAccountIdAndFileSha256(
        batch.getCorporateId(), batch.getSourceAccountId(), sha256);
    if (existingWithSameHash.isPresent() && !existingWithSameHash.get().getId().equals(batchId)) {
      throw new BusinessException("DUPLICATE_FILE", "This exact file has already been uploaded for this source account (Batch: " + existingWithSameHash.get().getBatchName() + ")");
    }

    // Upload original file to MinIO
      String originalKey = "corporates/" + batch.getCorporateId() + "/batches/" + batchId + "/original.xlsx";
      uploadToMinio(originalKey, tempFile, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    // Parse rows
      List<RowParsedData> parsedRows = parseWorkbook(tempFile);
    if (parsedRows.isEmpty()) {
      throw new BusinessException("EMPTY_FILE", "Excel file contains no data rows");
    }
    if (parsedRows.size() > MAX_ROWS) {
      throw new BusinessException("MAX_ROWS_EXCEEDED", "Excel file exceeds maximum allowed rows (" + MAX_ROWS + ")");
    }

    // Calculate totals from database to be 100% authoritative
    int totalItems = parsedRows.size();
    int validItems = (int) parsedRows.stream().filter(RowParsedData::valid).count();
    int invalidItems = totalItems - validItems;

    BigDecimal totalAmount = parsedRows.stream()
        .filter(RowParsedData::valid)
        .map(RowParsedData::amount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    String errorKey = null;
    if (invalidItems > 0) {
      byte[] errorExcel = generateErrorReportExcel(parsedRows);
      errorKey = "corporates/" + batch.getCorporateId() + "/batches/" + batchId + "/error-report.xlsx";
      uploadToMinio(errorKey, errorExcel, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

      PayoutBatchEntity saved = persistenceService.persist(
          corporateId, batchId, parsedRows, sha256, originalKey, errorKey);
      log.info("[EXCEL-INGESTION] Batch [{}] Ingested {} rows (Valid: {}, Invalid: {}, TotalAmount: {} {})",
        batchId, totalItems, validItems, invalidItems, totalAmount, batch.getCurrency());
      return toSummaryResponse(saved);
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw new BusinessException("EXCEL_INGESTION_FAILED", "Failed to ingest Excel file: " + e.getMessage());
    } finally {
      if (tempFile != null) {
        try { Files.deleteIfExists(tempFile); } catch (Exception e) { log.warn("Could not delete upload temp file", e); }
      }
    }
  }

  public BatchSummaryResponse toSummaryResponse(PayoutBatchEntity b) {
    return new BatchSummaryResponse(
        b.getId(), b.getCorporateId(), b.getSourceAccountId(), b.getSourceAccountNumber(),
        b.getBatchName(), b.getTotalItems(), b.getValidItems(), b.getInvalidItems(),
        b.getProcessedItems(), b.getSuccessfulItems(), b.getFailedItems(), b.getTotalAmount(),
        b.getTotalFee(), b.getCurrency(), b.getStatus(), b.getFileSha256(), b.getPolicyId(),
        b.getPolicyVersion(), b.getCanonicalPayloadHash(), b.getHoldId(), b.getCreatedBy(),
        b.getSubmittedBy(), b.getSubmittedAt(), b.getApprovedAt(), b.getStartedAt(),
        b.getCompletedAt(), b.getCreatedAt(), b.getUpdatedAt());
  }

  private String spoolAndHash(InputStream input, Path target) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] buffer = new byte[64 * 1024];
      byte[] magic = new byte[4];
      int magicLength = 0;
      long total = 0;
      try (InputStream source = input; OutputStream out = Files.newOutputStream(target)) {
        int read;
        while ((read = source.read(buffer)) != -1) {
          total += read;
          if (total > MAX_FILE_BYTES) {
            throw new BusinessException("FILE_TOO_LARGE", "Excel file exceeds 25 MB limit");
          }
          int copy = Math.min(read, magic.length - magicLength);
          if (copy > 0) {
            System.arraycopy(buffer, 0, magic, magicLength, copy);
            magicLength += copy;
          }
          digest.update(buffer, 0, read);
          out.write(buffer, 0, read);
        }
      }
      if (magicLength < 4 || magic[0] != 0x50 || magic[1] != 0x4B || magic[2] != 0x03 || magic[3] != 0x04) {
        throw new BusinessException("INVALID_FILE_FORMAT", "Only genuine .xlsx files are supported");
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw new BusinessException("INVALID_FILE", "Could not read uploaded file: " + e.getMessage());
    }
  }

  private List<RowParsedData> parseWorkbook(Path file) {
    List<RowParsedData> list = new ArrayList<>();
    Set<String> seenAccounts = new HashSet<>();
    try (OPCPackage pkg = OPCPackage.open(file.toFile())) {
      XSSFReader reader = new XSSFReader(pkg);
      try (InputStream sheet = reader.getSheetsData().next()) {
        XMLReader parser = XMLHelper.newXMLReader();
        parser.setContentHandler(new XSSFSheetXMLHandler(
            reader.getStylesTable(), null, reader.getSharedStringsTable(),
            new XSSFSheetXMLHandler.SheetContentsHandler() {
              private String[] cells;
              private int excelRow;

              @Override public void startRow(int rowNum) { cells = new String[8]; excelRow = rowNum + 1; }
              @Override public void cell(String ref, String value, XSSFComment comment) {
                int column = new org.apache.poi.ss.util.CellReference(ref).getCol();
                if (column < cells.length) cells[column] = value;
              }
              @Override public void endRow(int rowNum) {
                if (excelRow == 1) return;
                appendParsedRow(list, seenAccounts, excelRow, cells);
                if (list.size() > MAX_ROWS) {
                  throw new RowLimitExceededException();
                }
              }
            }, new DataFormatter(), false));
        parser.parse(new InputSource(sheet));
      }
    } catch (RowLimitExceededException e) {
      throw new BusinessException("MAX_ROWS_EXCEEDED", "Excel file exceeds maximum allowed rows (" + MAX_ROWS + ")");
    } catch (Exception e) {
      log.error("[EXCEL-PARSER-ERROR] Failed to parse Excel file", e);
      throw new BusinessException("EXCEL_PARSE_ERROR", "Failed to parse Excel file: " + e.getMessage());
    }
    return list;
  }

  private void appendParsedRow(List<RowParsedData> list, Set<String> seenAccounts, int rowIdx, String[] cells) {
        String employeeCode = cells[0];
        String beneficiaryName = cells[1];
        String accountNumber = cells[2];
        String bankCode = cells[3];
        String amountStr = cells[4];
        String description = cells[5];
        String employeeEmail = cells[6];
        String payrollPeriod = cells[7];
        if (isEmpty(employeeCode) && isEmpty(beneficiaryName) && isEmpty(accountNumber) && isEmpty(amountStr)) return;
        List<String> errors = new ArrayList<>();
        if (isEmpty(beneficiaryName)) {
          errors.add("Tên người thụ hưởng không được để trống");
        }
        if (isEmpty(accountNumber)) {
          errors.add("Số tài khoản không được để trống");
        }
        if (isEmpty(bankCode)) {
          errors.add("Mã ngân hàng không được để trống");
        }
        validateLength(errors, employeeCode, 64, "Mã nhân viên");
        validateLength(errors, beneficiaryName, 160, "Tên người thụ hưởng");
        validateLength(errors, accountNumber, 20, "Số tài khoản");
        validateLength(errors, bankCode, 32, "Mã ngân hàng");
        validateLength(errors, description, 255, "Nội dung");
        validateLength(errors, employeeEmail, 160, "Email");
        validateLength(errors, payrollPeriod, 20, "Kỳ lương");
        if (!isEmpty(employeeEmail)
            && !employeeEmail.trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
          errors.add("Email nhân viên không hợp lệ");
        }

        BigDecimal amount = BigDecimal.ZERO;
        if (isEmpty(amountStr)) {
          errors.add("Số tiền không được để trống");
        } else {
          try {
            amount = new BigDecimal(amountStr.replace(",", "").trim());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
              errors.add("Số tiền phải lớn hơn 0");
            }
            if (amount.scale() > 2 || amount.precision() > 19) {
              errors.add("Số tiền vượt quá định dạng cho phép (tối đa 19 chữ số, 2 số lẻ)");
            }
          } catch (Exception e) {
            errors.add("Số tiền không hợp lệ: " + amountStr);
          }
        }

        if (!isEmpty(accountNumber)) {
          String normalizedBank = isEmpty(bankCode) ? "" : bankCode.trim().toUpperCase();
          String normalizedAcc = normalizedBank + ":" + accountNumber.trim().toUpperCase();
          if (seenAccounts.contains(normalizedAcc)) {
            errors.add("Trùng số tài khoản trong cùng một danh sách");
          } else {
            seenAccounts.add(normalizedAcc);
          }
        }

        boolean valid = errors.isEmpty();
        String errorMsg = valid ? null : String.join("; ", errors);

        list.add(new RowParsedData(
            rowIdx,
            employeeCode != null ? employeeCode.trim() : null,
            beneficiaryName != null ? beneficiaryName.trim() : "",
            accountNumber != null ? accountNumber.trim() : "",
            bankCode != null ? bankCode.trim().toUpperCase() : "SYSTEM_BANK",
            amount,
            description != null ? description.trim() : "Chi trả lương",
            employeeEmail != null ? employeeEmail.trim() : null,
            payrollPeriod != null ? payrollPeriod.trim() : null,
            valid,
            errorMsg
        ));
  }

  private static final class RowLimitExceededException extends RuntimeException { }

  public byte[] generateErrorReportExcel(List<RowParsedData> rows) {
    try (Workbook workbook = new XSSFWorkbook();
         ByteArrayOutputStream out = new ByteArrayOutputStream()) {

      Sheet sheet = workbook.createSheet("Loi_Chi_Tiet");
      Row header = sheet.createRow(0);
      header.createCell(0).setCellValue("Dong");
      header.createCell(1).setCellValue("Ma_NV");
      header.createCell(2).setCellValue("Ten_Nguoi_Nhan");
      header.createCell(3).setCellValue("So_Tai_Khoan");
      header.createCell(4).setCellValue("Ngan_Hang");
      header.createCell(5).setCellValue("So_Tien");
      header.createCell(6).setCellValue("Trang_Thai");
      header.createCell(7).setCellValue("Loi_Chi_Tiet");

      int rowIdx = 1;
      for (RowParsedData r : rows) {
        if (!r.valid()) {
          Row row = sheet.createRow(rowIdx++);
          row.createCell(0).setCellValue(r.rowNumber());
          row.createCell(1).setCellValue(r.employeeCode() != null ? r.employeeCode() : "");
          row.createCell(2).setCellValue(r.beneficiaryName());
          row.createCell(3).setCellValue(r.accountNumber());
          row.createCell(4).setCellValue(r.bankCode());
          row.createCell(5).setCellValue(r.amount().toPlainString());
          row.createCell(6).setCellValue("LOI");
          row.createCell(7).setCellValue(r.errorMessage());
        }
      }

      workbook.write(out);
      return out.toByteArray();
    } catch (Exception e) {
      log.error("Failed to generate error Excel report", e);
      return new byte[0];
    }
  }

  public byte[] generateTemplateExcel() {
    try (Workbook workbook = new XSSFWorkbook();
         ByteArrayOutputStream out = new ByteArrayOutputStream()) {

      Sheet sheet = workbook.createSheet("Bang_Luong");
      Row header = sheet.createRow(0);
      header.createCell(0).setCellValue("employee_code*");
      header.createCell(1).setCellValue("beneficiary_name*");
      header.createCell(2).setCellValue("account_number*");
      header.createCell(3).setCellValue("bank_code*");
      header.createCell(4).setCellValue("amount*");
      header.createCell(5).setCellValue("description");
      header.createCell(6).setCellValue("employee_email");
      header.createCell(7).setCellValue("payroll_period");

      Row sample1 = sheet.createRow(1);
      sample1.createCell(0).setCellValue("EMP001");
      sample1.createCell(1).setCellValue("NGUYEN VAN A");
      sample1.createCell(2).setCellValue("1000000001");
      sample1.createCell(3).setCellValue("SYSTEM_BANK");
      sample1.createCell(4).setCellValue(15000000);
      sample1.createCell(5).setCellValue("Luong thang 08/2026");
      sample1.createCell(6).setCellValue("a.nguyen@corp.com");
      sample1.createCell(7).setCellValue("08/2026");

      Row sample2 = sheet.createRow(2);
      sample2.createCell(0).setCellValue("EMP002");
      sample2.createCell(1).setCellValue("TRAN THI B");
      sample2.createCell(2).setCellValue("970400000002");
      sample2.createCell(3).setCellValue("VCB");
      sample2.createCell(4).setCellValue(22000000);
      sample2.createCell(5).setCellValue("Luong thang 08/2026");
      sample2.createCell(6).setCellValue("b.tran@corp.com");
      sample2.createCell(7).setCellValue("08/2026");

      workbook.write(out);
      return out.toByteArray();
    } catch (Exception e) {
      log.error("Failed to generate template Excel", e);
      return new byte[0];
    }
  }

  public byte[] downloadErrorReport(UUID batchId) {
    PayoutBatchEntity batch = batchRepository.findById(batchId).orElseThrow(() ->
        new BusinessException("BATCH_NOT_FOUND", "Payout batch not found"));

    if (batch.getErrorReportFileKey() != null) {
      try (InputStream is = minioClient.getObject(
          io.minio.GetObjectArgs.builder().bucket(bucketName).object(batch.getErrorReportFileKey()).build())) {
        return is.readAllBytes();
      } catch (Exception e) {
        log.warn("Could not read error report from MinIO, generating on the fly", e);
      }
    }

    List<PayoutItemEntity> invalidItems = itemRepository.findByBatchIdAndStatus(batchId, "INVALID");
    List<RowParsedData> rows = invalidItems.stream().map(it -> new RowParsedData(
        it.getRowNumber(),
        it.getEmployeeCode(),
        it.getBeneficiaryName(),
        it.getAccountNumber(),
        it.getBankCode(),
        it.getAmount(),
        it.getDescription(),
        it.getEmployeeEmail(),
        it.getPayrollPeriod(),
        false,
        it.getValidationError()
    )).toList();
    return generateErrorReportExcel(rows);
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
      log.error("[MINIO-ERROR] Could not upload file [{}] to MinIO bucket [{}]: {}", key, bucketName, e.getMessage());
      throw new BusinessException("STORAGE_UPLOAD_FAILED", "Failed to upload file to storage: " + e.getMessage());
    }
  }

  private void uploadToMinio(String key, Path file, String contentType) {
    try (InputStream input = Files.newInputStream(file)) {
      minioClient.putObject(PutObjectArgs.builder().bucket(bucketName).object(key)
          .stream(input, Files.size(file), -1).contentType(contentType).build());
    } catch (Exception e) {
      throw new BusinessException("STORAGE_UPLOAD_FAILED", "Failed to upload file to storage: " + e.getMessage());
    }
  }

  private boolean isEmpty(String s) {
    return s == null || s.trim().isEmpty();
  }

  private void validateLength(List<String> errors, String value, int maxLength, String fieldName) {
    if (value != null && value.trim().length() > maxLength) {
      errors.add(fieldName + " vượt quá " + maxLength + " ký tự");
    }
  }
}
