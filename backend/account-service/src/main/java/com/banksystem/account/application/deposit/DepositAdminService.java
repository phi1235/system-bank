package com.banksystem.account.application.deposit;
import com.banksystem.account.application.account.*;
import com.banksystem.account.application.card.*;
import com.banksystem.account.application.deposit.*;
import com.banksystem.account.application.ledger.*;
import com.banksystem.account.domain.account.*;
import com.banksystem.account.domain.card.*;
import com.banksystem.account.domain.deposit.*;
import com.banksystem.account.domain.ledger.*;
import com.banksystem.account.api.dto.*;

import com.banksystem.account.api.dto.DepositDtos.AdminDepositFilterRequest;
import com.banksystem.account.api.dto.DepositDtos.AdminTermDepositRow;
import com.banksystem.account.api.dto.DepositDtos.DepositAdminSummaryResponse;
import com.banksystem.account.api.dto.DepositDtos.DepositProductResponse;
import com.banksystem.account.api.dto.DepositDtos.UpdateDepositProductRequest;
import com.banksystem.common.api.PageResponse;
import java.util.List;
import java.util.UUID;

public interface DepositAdminService {
  DepositAdminSummaryResponse summary();
  PageResponse<AdminTermDepositRow> list(AdminDepositFilterRequest req);
  PageResponse<AdminTermDepositRow> list(AdminDepositListQuery query);
  List<DepositProductResponse> allProducts();
  DepositProductResponse updateProduct(String code, UpdateDepositProductRequest req, UUID staffId);
}
