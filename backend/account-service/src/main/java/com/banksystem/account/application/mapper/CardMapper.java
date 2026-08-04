package com.banksystem.account.application.mapper;

import com.banksystem.account.api.dto.CardDtos.CardResponse;
import com.banksystem.account.domain.CardEntity;
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
