package com.banksystem.account.api.controller.deposit;

import com.banksystem.account.api.dto.deposit.DepositDtos.DepositProductResponse;
import com.banksystem.account.api.dto.deposit.DepositDtos.DepositQuoteResponse;
import com.banksystem.account.api.dto.deposit.DepositDtos.OpenDepositRequest;
import com.banksystem.account.api.dto.deposit.DepositDtos.TermDepositResponse;
import com.banksystem.account.application.deposit.TermDepositService;
import com.banksystem.common.api.ApiResponse;
import com.banksystem.common.security.UserContext;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer term deposits. HTTP only; rules in {@link TermDepositService}.
 * Gateway: {@code /api/v1/deposits/**} → ACCOUNT-SERVICE.
 */
@RestController
@RequestMapping("/api/v1/deposits")
public class CustomerDepositController {

  private final TermDepositService service;

  public CustomerDepositController(TermDepositService service) {
    this.service = service;
  }

  @GetMapping("/products")
  public ApiResponse<List<DepositProductResponse>> products() {
    UserContext.requireUser();
    return ApiResponse.ok(service.products());
  }

  /** Preview interest at maturity before opening. */
  @GetMapping("/quote")
  public ApiResponse<DepositQuoteResponse> quote(
      @RequestParam String productCode, @RequestParam BigDecimal amount) {
    UserContext.requireUser();
    return ApiResponse.ok(service.quote(productCode, amount));
  }

  @PostMapping
  public ApiResponse<TermDepositResponse> open(@Valid @RequestBody OpenDepositRequest request) {
    return ApiResponse.ok(service.open(request, UserContext.requireUser()));
  }

  @GetMapping
  public ApiResponse<List<TermDepositResponse>> mine() {
    return ApiResponse.ok(service.listMine(UserContext.requireUser().userId()));
  }

  @GetMapping("/{id}")
  public ApiResponse<TermDepositResponse> get(@PathVariable UUID id) {
    return ApiResponse.ok(service.get(id, UserContext.requireUser()));
  }

  @PostMapping("/{id}/close")
  public ApiResponse<TermDepositResponse> closeEarly(@PathVariable UUID id) {
    return ApiResponse.ok(service.closeEarly(id, UserContext.requireUser()));
  }
}
