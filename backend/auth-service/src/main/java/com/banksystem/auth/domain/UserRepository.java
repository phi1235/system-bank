package com.banksystem.auth.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
  Optional<UserEntity> findByUsername(String username);

  boolean existsByUsername(String username);

  boolean existsByEmail(String email);

  @Query("""
      SELECT u FROM UserEntity u
      WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%'))
         OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%'))
      """)
  Page<UserEntity> search(@Param("q") String q, Pageable pageable);
}
