package com.banksystem.account.domain.sweep;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SweepProductRepository extends JpaRepository<SweepProductEntity, String> {
  List<SweepProductEntity> findByActiveTrueOrderByCodeAsc();
}
