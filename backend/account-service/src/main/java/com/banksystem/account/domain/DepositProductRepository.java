package com.banksystem.account.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepositProductRepository extends JpaRepository<DepositProductEntity, String> {

  List<DepositProductEntity> findByActiveTrueOrderByTenorMonthsAsc();
}
