package com.banksystem.customer.api.customer;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.common.security.UserContext;
import com.banksystem.customer.api.dto.KycDtos.KycCaseResponse;
import com.banksystem.customer.application.kyc.KycWorkflowService;
import com.banksystem.customer.application.kyc.KycWorkflowService.DocumentDownload;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/customers/me/kyc")
public class CustomerKycController {

  private final KycWorkflowService service;

  public CustomerKycController(KycWorkflowService service) {
    this.service = service;
  }

  @GetMapping
  @RequirePermission("ib:profile:view")
  public ApiResponse<KycCaseResponse> getMine() {
    return ApiResponse.ok(service.getByCustomer(UserContext.requireUser().userId()));
  }

  @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @RequirePermission("ib:profile:edit")
  public ApiResponse<KycCaseResponse> upload(
      @RequestParam String documentType, @RequestParam MultipartFile file) {
    return ApiResponse.ok(service.upload(
        UserContext.requireUser().userId(), documentType, file));
  }

  @PostMapping("/submit")
  @RequirePermission("ib:profile:edit")
  public ApiResponse<KycCaseResponse> submit() {
    return ApiResponse.ok(service.submit(UserContext.requireUser().userId()));
  }

  @GetMapping("/documents/{id}/content")
  @RequirePermission("ib:profile:view")
  public ResponseEntity<InputStreamResource> download(@PathVariable UUID id) {
    UUID userId = UserContext.requireUser().userId();
    return fileResponse(service.download(id, userId, false));
  }

  static ResponseEntity<InputStreamResource> fileResponse(DocumentDownload download) {
    MediaType mediaType = MediaType.parseMediaType(download.contentType());
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(mediaType);
    headers.setContentDisposition(ContentDisposition.inline().filename(download.fileName()).build());
    if (download.object().size() >= 0) {
      headers.setContentLength(download.object().size());
    }
    return ResponseEntity.ok().headers(headers)
        .body(new InputStreamResource(download.object().content()));
  }
}
