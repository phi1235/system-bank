package com.banksystem.transaction.api.openbanking;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.iso20022.Pain001Dto;
import com.banksystem.common.iso20022.Pain002Dto;
import com.banksystem.common.security.B2bClientPrincipal;
import com.banksystem.common.security.B2bContext;
import com.banksystem.common.security.SecurityHeaders;
import com.banksystem.transaction.api.dto.OpenBankingDtos.OpenBankingRecordFilterRequest;
import com.banksystem.transaction.application.openbanking.OpenBankingPaymentService;
import com.banksystem.transaction.application.openbanking.OpenBankingRecordSearchQuery;
import com.banksystem.transaction.domain.openbanking.IsoPaymentRecordEntity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/open-banking/v1/payments")
public class OpenBankingPaymentController {

  private final OpenBankingPaymentService paymentService;

  public OpenBankingPaymentController(OpenBankingPaymentService paymentService) {
    this.paymentService = paymentService;
  }

  @PostMapping(consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE}, produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
  public ResponseEntity<Pain002Dto> initiateSinglePayment(
      @RequestBody Pain001Dto pain001,
      @RequestHeader(value = SecurityHeaders.JWS_SIGNATURE, required = false) String jwsSignature,
      HttpServletRequest httpRequest) {
    B2bContext.requireScope(SecurityHeaders.SCOPE_OPENBANKING_PAYMENTS_WRITE);
    B2bClientPrincipal client = B2bContext.requireClient();

    Pain002Dto response = paymentService.initiateSinglePayment(client.clientId(), pain001, jwsSignature);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PostMapping(value = "/bulk", consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE}, produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
  public ResponseEntity<Pain002Dto> initiateBulkPayments(
      @RequestBody Pain001Dto pain001,
      @RequestHeader(value = SecurityHeaders.JWS_SIGNATURE, required = false) String jwsSignature,
      HttpServletRequest httpRequest) {
    B2bContext.requireScope(SecurityHeaders.SCOPE_OPENBANKING_PAYMENTS_BULK_WRITE);
    B2bClientPrincipal client = B2bContext.requireClient();

    Pain002Dto response = paymentService.initiateBulkPayments(client.clientId(), pain001, jwsSignature);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
  }

  @GetMapping(value = "/{paymentId}/status", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
  public ResponseEntity<Pain002Dto> getPaymentStatus(@PathVariable String paymentId) {
    B2bContext.requireScope(SecurityHeaders.SCOPE_OPENBANKING_PAYMENTS_READ);
    B2bClientPrincipal client = B2bContext.requireClient();
    Pain002Dto statusReport = paymentService.getPaymentStatus(client.clientId(), paymentId);
    return ResponseEntity.ok(statusReport);
  }

  @GetMapping(value = "/records", produces = MediaType.APPLICATION_JSON_VALUE)
  public ApiResponse<Page<IsoPaymentRecordEntity>> listPaymentRecords(
      @Valid @ModelAttribute OpenBankingRecordFilterRequest req) {
    B2bContext.requireScope(SecurityHeaders.SCOPE_OPENBANKING_PAYMENTS_READ);
    B2bClientPrincipal client = B2bContext.requireClient();
    OpenBankingRecordSearchQuery query = OpenBankingRecordSearchQuery.of(client.clientId(), req);
    return ApiResponse.ok(paymentService.listPaymentRecords(query));
  }
}
