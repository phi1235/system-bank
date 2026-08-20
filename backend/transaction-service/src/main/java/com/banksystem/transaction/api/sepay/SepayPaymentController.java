package com.banksystem.transaction.api.sepay;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.UserContext;
import com.banksystem.transaction.api.dto.SepayDtos.CreateTopUpRequest;
import com.banksystem.transaction.api.dto.SepayDtos.SepayWebhookPayload;
import com.banksystem.transaction.api.dto.SepayDtos.SepayWebhookResponse;
import com.banksystem.transaction.api.dto.SepayDtos.TopUpOrderResponse;
import com.banksystem.transaction.application.sepay.SepayService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments/sepay")
public class SepayPaymentController {

  private final SepayService sepayService;

  public SepayPaymentController(SepayService sepayService) {
    this.sepayService = sepayService;
  }

  @PostMapping("/topup")
  public ApiResponse<TopUpOrderResponse> createTopUpOrder(
      @Valid @RequestBody CreateTopUpRequest request) {
    return ApiResponse.ok(sepayService.createTopUpOrder(UserContext.requireUser().userId(), request));
  }

  @GetMapping("/orders/{orderCode}")
  public ApiResponse<TopUpOrderResponse> getOrderByCode(@PathVariable("orderCode") String orderCode) {
    return ApiResponse.ok(sepayService.getOrderByCode(orderCode));
  }

  @GetMapping("/my-orders")
  public ApiResponse<List<TopUpOrderResponse>> getMyOrders() {
    return ApiResponse.ok(sepayService.getMyOrders(UserContext.requireUser().userId()));
  }

  @PostMapping("/webhook")
  public SepayWebhookResponse handleWebhook(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @RequestBody SepayWebhookPayload payload) {
    return sepayService.processWebhook(authHeader, payload);
  }
}
