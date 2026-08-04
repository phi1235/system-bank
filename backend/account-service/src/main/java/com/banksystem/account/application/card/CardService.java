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

import com.banksystem.account.api.dto.CardDtos.CardResponse;
import com.banksystem.account.api.dto.CardDtos.CardRevealResponse;
import com.banksystem.account.api.dto.CardDtos.UpdateCardLimitRequest;
import com.banksystem.common.security.GatewayUser;
import java.util.List;
import java.util.UUID;

public interface CardService {
  CardResponse request(UUID accountId, GatewayUser user);
  CardResponse activate(UUID cardId, GatewayUser user);
  CardResponse lock(UUID cardId, GatewayUser user);
  CardResponse unlock(UUID cardId, GatewayUser user);
  CardResponse close(UUID cardId, GatewayUser user);
  CardResponse updateLimit(UUID cardId, UpdateCardLimitRequest request, GatewayUser user);
  CardRevealResponse reveal(UUID cardId, GatewayUser user);
  List<CardResponse> listMine(UUID userId);
}
