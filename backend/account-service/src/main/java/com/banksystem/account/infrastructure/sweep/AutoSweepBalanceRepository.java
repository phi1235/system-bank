package com.banksystem.account.infrastructure.sweep;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AutoSweepBalanceRepository {
  private final JdbcTemplate jdbcTemplate;

  public AutoSweepBalanceRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public boolean creditIfActive(UUID accountId, BigDecimal amount) {
    return jdbcTemplate.update("""
        UPDATE accounts
        SET balance = balance + ?, updated_at = NOW()
        WHERE id = ? AND status = 'ACTIVE'
        """, amount, accountId) == 1;
  }

  public boolean debitIfAvailable(UUID accountId, BigDecimal amount) {
    return jdbcTemplate.update("""
        UPDATE accounts
        SET balance = balance - ?, updated_at = NOW()
        WHERE id = ? AND status = 'ACTIVE' AND available_balance >= ?
        """, amount, accountId, amount) == 1;
  }

  public BalanceState balance(UUID accountId) {
    return jdbcTemplate.queryForObject(
        "SELECT balance, available_balance FROM accounts WHERE id = ?",
        (rs, row) -> new BalanceState(rs.getBigDecimal(1), rs.getBigDecimal(2)), accountId);
  }

  public record BalanceState(BigDecimal booked, BigDecimal available) {}
}
