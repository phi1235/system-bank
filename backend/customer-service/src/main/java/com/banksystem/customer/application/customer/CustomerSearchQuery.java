package com.banksystem.customer.application.customer;
import com.banksystem.customer.application.customer.*;
import com.banksystem.customer.application.support.*;
import com.banksystem.customer.application.dashboard.*;
import com.banksystem.customer.domain.customer.*;
import com.banksystem.customer.domain.support.*;
import com.banksystem.customer.api.dto.*;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.customer.api.dto.CustomerDtos.CustomerSearchFilterRequest;
import java.util.Set;

public record CustomerSearchQuery(
    String qNorm,
    boolean hasQ,
    String kycNorm,
    boolean hasKyc,
    int page,
    int size
) {

  private static final Set<String> KYC_STATUSES = Set.of("PENDING", "VERIFIED", "REJECTED");

  public static CustomerSearchQuery of(CustomerSearchFilterRequest req) {
    if (req == null) {
      return new CustomerSearchQuery("", false, "PENDING", false, 0, 20);
    }
    return of(req.q(), req.kycStatus(), req.page(), req.size());
  }

  public static CustomerSearchQuery of(String q, String kycStatus, Integer page, Integer size) {
    String qTrim = q == null ? "" : q.trim();
    boolean hasQ = !qTrim.isEmpty();

    String kyc = kycStatus == null ? "" : kycStatus.trim().toUpperCase();
    boolean hasKyc = !kyc.isEmpty();

    if (hasKyc && !KYC_STATUSES.contains(kyc)) {
      throw new BusinessException("INVALID_KYC_STATUS", "kycStatus must be PENDING|VERIFIED|REJECTED");
    }

    int p = (page == null || page < 0) ? 0 : page;
    int s = (size == null || size < 1) ? 20 : Math.min(size, 100);

    return new CustomerSearchQuery(
        qTrim,
        hasQ,
        hasKyc ? kyc : "PENDING",
        hasKyc,
        p,
        s
    );
  }
}
