package com.banksystem.account.infrastructure.sweep;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AutoSweepProfileQueryRepository {
  private static final String SELECT_PROFILE = """
      SELECT p.id, p.source_account_id, a.account_number, p.product_code, p.status,
             p.threshold_amount, p.min_sweep_amount, sp.annual_rate_bps,
             a.balance AS casa_balance, a.available_balance,
             pos.principal_balance, pos.accrued_interest,
             a.balance + pos.principal_balance AS total_liquidity,
             p.last_sweep_business_date, p.version, p.updated_at
      FROM auto_sweep_profiles p
      JOIN accounts a ON a.id = p.source_account_id
      JOIN auto_sweep_positions pos ON pos.profile_id = p.id
      JOIN sweep_products sp ON sp.code = p.product_code
      """;

  private final JdbcTemplate jdbcTemplate;

  public AutoSweepProfileQueryRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<ProfileRow> findByUserId(UUID userId) {
    return jdbcTemplate.query(
        SELECT_PROFILE + " WHERE p.user_id = ? ORDER BY p.created_at DESC",
        this::map, userId);
  }

  public Optional<ProfileRow> findById(UUID profileId) {
    return jdbcTemplate.query(
        SELECT_PROFILE + " WHERE p.id = ?", this::map, profileId).stream().findFirst();
  }

  public ProfilePage findAll(int page, int size) {
    long total = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM auto_sweep_profiles", Long.class);
    List<ProfileRow> items = jdbcTemplate.query(
        SELECT_PROFILE + " ORDER BY p.created_at DESC LIMIT ? OFFSET ?",
        this::map, size, Math.multiplyExact((long) page, size));
    return new ProfilePage(items, total);
  }

  private ProfileRow map(ResultSet rs, int rowNumber) throws SQLException {
    return new ProfileRow(
        rs.getObject("id", UUID.class),
        rs.getObject("source_account_id", UUID.class),
        rs.getString("account_number"),
        rs.getString("product_code"),
        rs.getString("status"),
        rs.getBigDecimal("threshold_amount"),
        rs.getBigDecimal("min_sweep_amount"),
        rs.getInt("annual_rate_bps"),
        rs.getBigDecimal("casa_balance"),
        rs.getBigDecimal("available_balance"),
        rs.getBigDecimal("principal_balance"),
        rs.getBigDecimal("accrued_interest"),
        rs.getBigDecimal("total_liquidity"),
        rs.getObject("last_sweep_business_date", LocalDate.class),
        rs.getLong("version"),
        rs.getTimestamp("updated_at").toInstant());
  }

  public record ProfilePage(List<ProfileRow> items, long totalElements) {}

  public record ProfileRow(
      UUID id,
      UUID sourceAccountId,
      String sourceAccountNumber,
      String productCode,
      String status,
      BigDecimal thresholdAmount,
      BigDecimal minSweepAmount,
      int annualRateBps,
      BigDecimal casaBalance,
      BigDecimal availableBalance,
      BigDecimal flexiblePrincipal,
      BigDecimal accruedInterest,
      BigDecimal totalLiquidity,
      LocalDate lastSweepBusinessDate,
      long version,
      Instant updatedAt) {}
}
