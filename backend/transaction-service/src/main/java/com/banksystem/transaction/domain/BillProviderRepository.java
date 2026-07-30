package com.banksystem.transaction.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillProviderRepository extends JpaRepository<BillProviderEntity, String> {
  List<BillProviderEntity> findAllByCategoryIdAndActiveTrue(String categoryId);
  List<BillProviderEntity> findAllByActiveTrue();
}
