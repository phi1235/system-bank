package com.banksystem.account.domain.repository.deposit;

import com.banksystem.account.domain.entity.deposit.DepositProductEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepositProductRepository extends JpaRepository<DepositProductEntity, String> {

  List<DepositProductEntity> findByActiveTrueOrderByTenorMonthsAsc();

  List<DepositProductEntity> findAllByOrderByTenorMonthsAsc();
}
