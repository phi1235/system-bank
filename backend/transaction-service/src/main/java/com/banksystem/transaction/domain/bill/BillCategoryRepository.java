package com.banksystem.transaction.domain.bill;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillCategoryRepository extends JpaRepository<BillCategoryEntity, String> {
  List<BillCategoryEntity> findAllByActiveTrueOrderByDisplayOrder();
}
