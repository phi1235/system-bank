package com.banksystem.transaction.api.transfer;

import com.banksystem.common.api.ApiResponse;
import com.banksystem.transaction.application.transfer.BankCatalogService;
import com.banksystem.transaction.application.transfer.BankCatalogService.BankItem;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class BankCatalogController {

  private final BankCatalogService bankCatalogService;

  public BankCatalogController(BankCatalogService bankCatalogService) {
    this.bankCatalogService = bankCatalogService;
  }

  @GetMapping({"/banks", "/transactions/banks"})
  public ApiResponse<List<BankItem>> listBanks() {
    return ApiResponse.ok(bankCatalogService.listBanks());
  }
}
