package com.banksystem.account.domain.deposit;
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
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepositProductRepository extends JpaRepository<DepositProductEntity, String> {

  List<DepositProductEntity> findByActiveTrueOrderByTenorMonthsAsc();

  List<DepositProductEntity> findAllByOrderByTenorMonthsAsc();
}
