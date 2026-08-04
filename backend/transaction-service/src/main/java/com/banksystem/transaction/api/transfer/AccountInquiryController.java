package com.banksystem.transaction.api.transfer;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.transaction.application.transfer.AccountInquiryService;
import com.banksystem.transaction.application.transfer.AccountInquiryService.InquiryRequest;
import com.banksystem.transaction.application.transfer.AccountInquiryService.InquiryResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
public class AccountInquiryController {

  private final AccountInquiryService inquiryService;

  public AccountInquiryController(AccountInquiryService inquiryService) {
    this.inquiryService = inquiryService;
  }

  @PostMapping("/account-inquiry")
  public ApiResponse<InquiryResponse> inquire(@Valid @RequestBody InquiryRequest req) {
    return ApiResponse.ok(inquiryService.inquire(req));
  }
}
