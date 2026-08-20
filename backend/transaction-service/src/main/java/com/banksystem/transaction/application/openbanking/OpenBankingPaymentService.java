package com.banksystem.transaction.application.openbanking;

import com.banksystem.common.iso20022.Pain001Dto;
import com.banksystem.common.iso20022.Pain002Dto;
import com.banksystem.transaction.domain.openbanking.IsoPaymentRecordEntity;
import org.springframework.data.domain.Page;

public interface OpenBankingPaymentService {

  Pain002Dto initiateSinglePayment(String clientId, Pain001Dto pain001, String signaturePayload);

  Pain002Dto initiateBulkPayments(String clientId, Pain001Dto pain001, String signaturePayload);

  Pain002Dto getPaymentStatus(String clientId, String paymentId);

  Page<IsoPaymentRecordEntity> listPaymentRecords(OpenBankingRecordSearchQuery query);
}
