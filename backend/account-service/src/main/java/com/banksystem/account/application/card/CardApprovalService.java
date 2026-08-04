package com.banksystem.account.application.card;
import com.banksystem.account.application.account.*;
import com.banksystem.account.application.card.*;
import com.banksystem.account.application.deposit.*;
import com.banksystem.account.application.ledger.*;
import com.banksystem.account.domain.account.*;
import com.banksystem.account.domain.card.*;
import com.banksystem.account.domain.deposit.*;
import com.banksystem.account.domain.ledger.*;
import com.banksystem.account.api.dto.*;

import com.banksystem.account.api.dto.CardDtos.AdminCardFilterRequest;
import com.banksystem.account.api.dto.CardDtos.AdminCardRow;
import com.banksystem.account.api.dto.CardDtos.BatchApproveResult;
import com.banksystem.account.api.dto.CardDtos.CardResponse;
import com.banksystem.common.api.PageResponse;
import java.util.List;
import java.util.UUID;

public interface CardApprovalService {
  PageResponse<AdminCardRow> queue(AdminCardFilterRequest req);
  PageResponse<AdminCardRow> queue(String status, Integer page, Integer size, String q);
  PageResponse<AdminCardRow> queue(String status, Integer page, Integer size);
  BatchApproveResult batchApprove(List<UUID> cardIds, UUID staffId);
  CardResponse approve(UUID cardId, UUID staffId);
  CardResponse reject(UUID cardId, String reason, UUID staffId);
}
