package com.banksystem.transaction.application.transfer;

import com.banksystem.common.api.PageResponse;
import com.banksystem.common.security.GatewayUser;
import com.banksystem.transaction.api.dto.TransferDtos.AdminTransferFilterRequest;
import com.banksystem.transaction.api.dto.TransferDtos.TransferDetailResponse;
import com.banksystem.transaction.api.dto.TransferDtos.TransferQuoteResponse;
import com.banksystem.transaction.api.dto.TransferDtos.TransferRequest;
import com.banksystem.transaction.api.dto.TransferDtos.TransferResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface TransferService {
  TransferResponse transfer(GatewayUser user, String idempotencyKey, TransferRequest req, String ip);
  TransferQuoteResponse quote(UUID userId, BigDecimal amount);
  PageResponse<TransferResponse> myHistory(UUID userId, com.banksystem.transaction.api.dto.TransferDtos.MyTransferFilterRequest req);
  PageResponse<TransferResponse> myHistory(UUID userId, int page, int size, String status, Instant from, Instant to);
  Object adminTransfers(AdminTransferFilterRequest req);
  TransferResponse get(UUID id, GatewayUser user);
  TransferDetailResponse getDetail(UUID id, GatewayUser user);
}
