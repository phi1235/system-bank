package com.banksystem.account.application.ledger;
import com.banksystem.account.application.account.*;
import com.banksystem.account.application.card.*;
import com.banksystem.account.application.deposit.*;
import com.banksystem.account.application.ledger.*;
import com.banksystem.account.domain.account.*;
import com.banksystem.account.domain.card.*;
import com.banksystem.account.domain.deposit.*;
import com.banksystem.account.domain.ledger.*;
import com.banksystem.account.api.dto.*;

import com.banksystem.account.api.dto.AccountDtos.InternalLedgerEntryResponse;
import java.util.List;

public interface LedgerQueryService {
  List<InternalLedgerEntryResponse> searchByReferenceIds(List<String> referenceIds);
}
