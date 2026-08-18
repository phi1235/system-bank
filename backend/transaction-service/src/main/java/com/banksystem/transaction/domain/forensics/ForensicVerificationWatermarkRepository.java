package com.banksystem.transaction.domain.forensics;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ForensicVerificationWatermarkRepository
    extends JpaRepository<ForensicVerificationWatermarkEntity, String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT w FROM ForensicVerificationWatermarkEntity w WHERE w.jobName = :jobName")
  Optional<ForensicVerificationWatermarkEntity> lockByJobName(@Param("jobName") String jobName);
}
