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

  java.util.Optional<UserEntity> findByEmailIgnoreCase(String email);

  @Query("""
      SELECT u FROM UserEntity u
      WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%'))
         OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%'))
      """)
  Page<UserEntity> search(@Param("q") String q, Pageable pageable);

  /**
   * Admin user search. Callers pass boolean flags so Postgres never sees untyped NULL binds.
   * Free-text q matches username / email only (not UUID cast).
   */
  @Query("""
      SELECT u FROM UserEntity u
      WHERE (:hasUserId = false OR u.id = :userId)
        AND (:hasEnabled = false OR u.enabled = :enabled)
        AND (:hasQ = false
          OR LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%'))
          OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')))
      ORDER BY u.createdAt DESC
      """)
  Page<UserEntity> searchAdmin(
      @Param("hasUserId") boolean hasUserId,
      @Param("userId") UUID userId,
      @Param("hasEnabled") boolean hasEnabled,
      @Param("enabled") boolean enabled,
      @Param("hasQ") boolean hasQ,
      @Param("q") String q,
      Pageable pageable);
}
