package com.banksystem.account.infrastructure.mybatis;

import com.banksystem.account.api.dto.deposit.DepositDtos.DepositTenorRow;
import com.banksystem.account.api.dto.deposit.DepositDtos.DepositTotalsRow;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Funding summary over term_deposits (hand-written SQL in
 * resources/mybatis/DepositReportMapper.xml). Read model only — writes stay on JPA.
 */
@Mapper
public interface DepositReportMapper {

  DepositTotalsRow totals(@Param("today") LocalDate today);

  List<DepositTenorRow> byProduct();
}
