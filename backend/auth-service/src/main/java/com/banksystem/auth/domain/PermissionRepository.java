package com.banksystem.auth.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<PermissionEntity, String> {
  List<PermissionEntity> findAllByOrderByCodeAsc();
}
