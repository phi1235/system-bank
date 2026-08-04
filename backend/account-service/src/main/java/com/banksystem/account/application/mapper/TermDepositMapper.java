package com.banksystem.account.application.mapper;

import com.banksystem.account.api.dto.DepositDtos.DepositProductResponse;
import com.banksystem.account.api.dto.DepositDtos.TermDepositResponse;
import com.banksystem.account.application.DepositInterestCalculator;
import com.banksystem.account.domain.DepositProductEntity;
import com.banksystem.account.domain.TermDepositEntity;
import com.banksystem.account.domain.TermDepositStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

@Component
public class TermDepositMapper {

  public TermDepositResponse toResponse(TermDepositEntity d, int tenorMonths, ZoneId zone) {
    BigDecimal interest;
    if (d.getStatus() == TermDepositStatus.OPEN) {
      LocalDate openDate = LocalDate.ofInstant(d.getOpenedAt(), zone);
      long days = DepositInterestCalculator.daysBetween(openDate, d.getMaturityDate());
      interest = DepositInterestCalculator.interest(d.getAmount(), d.getRateBps(), days);
    } else {
      interest = d.getAccruedInterest();
    }
    return new TermDepositResponse(
        d.getId().toString(),
        d.getSourceAccountId().toString(),
        d.getProductCode(),
        tenorMonths,
        d.getAmount(),
        d.getRateBps(),
        d.getEarlyRateBps(),
        d.getOpenedAt(),
        d.getMaturityDate(),
        d.getStatus().name(),
        interest,
        d.getClosedAt());
  }

  public DepositProductResponse toProductResponse(DepositProductEntity p) {
    return new DepositProductResponse(
        p.getCode(),
        p.getTenorMonths(),
        p.getRateBps(),
        p.getEarlyRateBps(),
        p.getMinAmount(),
        p.isActive());
  }
}
