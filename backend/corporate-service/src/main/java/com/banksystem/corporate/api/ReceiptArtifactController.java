package com.banksystem.corporate.api;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.GatewayUser;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.common.security.UserContext;
import com.banksystem.corporate.api.dto.PayoutBatchDtos.ReceiptArtifactResponse;
import com.banksystem.corporate.application.receipt.ReceiptService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/corporations/{corporateId}/receipts")
public class ReceiptArtifactController {

  private final ReceiptService receiptService;

  public ReceiptArtifactController(ReceiptService receiptService) {
    this.receiptService = receiptService;
  }

  @GetMapping("/batches/{batchId}")
  @RequirePermission("corp:portal:view")
  public ResponseEntity<ApiResponse<List<ReceiptArtifactResponse>>> listBatchReceipts(
      @PathVariable UUID corporateId,
      @PathVariable UUID batchId) {
    GatewayUser user = UserContext.requireUser();
    List<ReceiptArtifactResponse> response = receiptService.listBatchReceipts(corporateId, batchId, user.userId());
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @GetMapping("/{artifactId}/download")
  @RequirePermission("corp:portal:view")
  public ResponseEntity<byte[]> downloadReceipt(
      @PathVariable UUID corporateId,
      @PathVariable UUID artifactId) {
    GatewayUser user = UserContext.requireUser();
    byte[] content = receiptService.downloadReceipt(corporateId, artifactId, user.userId());
    String filename = "receipt-" + artifactId + ".pdf";
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .body(content);
  }
}
