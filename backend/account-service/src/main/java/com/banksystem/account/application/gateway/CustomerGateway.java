package com.banksystem.account.application.gateway;
import com.banksystem.account.application.account.*;
import com.banksystem.account.application.card.*;
import com.banksystem.account.application.deposit.*;
import com.banksystem.account.application.ledger.*;
import com.banksystem.account.domain.account.*;
import com.banksystem.account.domain.card.*;
import com.banksystem.account.domain.deposit.*;
import com.banksystem.account.domain.ledger.*;
import com.banksystem.account.api.dto.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface CustomerGateway {
  Map<String, String> getCustomerNames(List<UUID> userIds);
}
