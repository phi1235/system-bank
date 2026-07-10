package com.banksystem.auth.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<RoleEntity, String> {
  List<RoleEntity> findByStaffTrueOrderByCodeAsc();

  List<RoleEntity> findAllByOrderByCodeAsc();
}
