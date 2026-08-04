package com.banksystem.account.application.mapper;
import com.banksystem.account.application.account.*;
import com.banksystem.account.application.card.*;
import com.banksystem.account.application.deposit.*;
import com.banksystem.account.application.ledger.*;
import com.banksystem.account.domain.account.*;
import com.banksystem.account.domain.card.*;
import com.banksystem.account.domain.deposit.*;
import com.banksystem.account.domain.ledger.*;
import com.banksystem.account.api.dto.*;

import com.banksystem.account.api.dto.CardDtos.CardResponse;
import org.springframework.stereotype.Component;

@Component
public class CardMapper {

  public CardResponse toResponse(CardEntity c, String accountNumber) {
    String maskedPan = c.getPanLast4() == null ? null : "9704 **** **** " + c.getPanLast4();
    return new CardResponse(
        c.getId().toString(),
        c.getAccountId().toString(),
        accountNumber,
        maskedPan,
        c.getBrand(),
        c.getStatus().name(),
        c.getDailyLimit(),
        c.getExpiresOn(),
        c.getRejectReason(),
        c.getCreatedAt());
  }
}
