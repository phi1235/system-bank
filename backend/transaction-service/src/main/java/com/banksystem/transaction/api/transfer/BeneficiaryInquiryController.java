package com.banksystem.transaction.api.transfer;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.GatewayUser;
import com.banksystem.common.security.RequireAnyPermission;
import com.banksystem.common.security.SecurityHeaders;
import com.banksystem.common.security.UserContext;
import com.banksystem.transaction.application.transfer.BeneficiaryInquiryQuery;
import com.banksystem.transaction.application.transfer.BeneficiaryInquiryService;
import com.banksystem.transaction.application.transfer.BeneficiaryInquiryService.InquiryRequest;
import com.banksystem.transaction.application.transfer.BeneficiaryInquiryService.InquiryResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class BeneficiaryInquiryController {

  private final BeneficiaryInquiryService inquiryService;

  public BeneficiaryInquiryController(BeneficiaryInquiryService inquiryService) {
    this.inquiryService = inquiryService;
  }

  @PostMapping("/beneficiary-inquiries")
  @RequireAnyPermission({
      SecurityHeaders.PERM_IB_TRANSFER_VIEW,
      SecurityHeaders.PERM_IB_TRANSFER_EXECUTE,
      SecurityHeaders.PERM_TX_LIST_VIEW
  })
  public ApiResponse<InquiryResponse> inquire(@Valid @RequestBody InquiryRequest req) {
    GatewayUser user = UserContext.requireUser();
    BeneficiaryInquiryQuery query = BeneficiaryInquiryQuery.of(req);
    return ApiResponse.ok(inquiryService.inquire(user.userId(), query));
  }
}
