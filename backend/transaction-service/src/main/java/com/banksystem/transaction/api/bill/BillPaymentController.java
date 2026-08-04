package com.banksystem.transaction.api.bill;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.api.PageResponse;
import com.banksystem.common.security.RequirePermission;
import com.banksystem.common.security.UserContext;
import com.banksystem.transaction.api.dto.BillDtos.BillCategoryResponse;
import com.banksystem.transaction.api.dto.BillDtos.BillInquiryRequest;
import com.banksystem.transaction.api.dto.BillDtos.BillInquiryResponse;
import com.banksystem.transaction.api.dto.BillDtos.BillPayRequest;
import com.banksystem.transaction.api.dto.BillDtos.BillPayResponse;
import com.banksystem.transaction.api.dto.BillDtos.BillPaymentHistoryResponse;
import com.banksystem.transaction.api.dto.BillDtos.BillProviderResponse;
import com.banksystem.transaction.application.bill.BillPaymentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bills")
public class BillPaymentController {

  private final BillPaymentService billService;

  public BillPaymentController(BillPaymentService billService) {
    this.billService = billService;
  }

  /** List active bill categories (Electricity, Water, Internet, Mobile Top-up). */
  @GetMapping("/categories")
  @RequirePermission("ib:bills:view")
  public ApiResponse<List<BillCategoryResponse>> categories() {
    return ApiResponse.ok(billService.listCategories());
  }

  /** List providers for a category, or all if no categoryId given. */
  @GetMapping("/providers")
  @RequirePermission("ib:bills:view")
  public ApiResponse<List<BillProviderResponse>> providers(
      @RequestParam(required = false) String categoryId) {
    return ApiResponse.ok(billService.listProviders(categoryId));
  }

  /** Inquire bill details for a customer code at a given provider. */
  @PostMapping("/inquiry")
  @RequirePermission("ib:bills:view")
  public ApiResponse<BillInquiryResponse> inquiry(@Valid @RequestBody BillInquiryRequest req) {
    return ApiResponse.ok(billService.inquireBill(req));
  }

  /** Execute bill payment. */
  @PostMapping("/pay")
  @RequirePermission("ib:bills:execute")
  public ApiResponse<BillPayResponse> pay(@Valid @RequestBody BillPayRequest req) {
    return ApiResponse.ok(billService.payBill(UserContext.requireUser().userId(), req));
  }

  /** Payment history for current customer. */
  @GetMapping("/history")
  @RequirePermission("ib:bills:view")
  public ApiResponse<PageResponse<BillPaymentHistoryResponse>> history(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.ok(billService.history(UserContext.requireUser().userId(), page, size));
  }

  @PostMapping("/history/findBillPaymentHistory")
  @RequirePermission("ib:bills:view")
  public ApiResponse<PageResponse<BillPaymentHistoryResponse>> findBillPaymentHistory(
      @Valid @RequestBody PageFilterRequest req) {
    return ApiResponse.ok(billService.history(UserContext.requireUser().userId(), req.page(), req.size()));
  }

  public record PageFilterRequest(Integer page, Integer size) {}
}
