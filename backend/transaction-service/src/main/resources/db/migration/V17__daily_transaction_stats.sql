CREATE TABLE daily_transaction_stats (
    day DATE NOT NULL,
    status VARCHAR(50) NOT NULL,
    from_account_id UUID NOT NULL,
    total_count BIGINT NOT NULL DEFAULT 0,
    total_amount NUMERIC(38,2) NOT NULL DEFAULT 0.00,
    total_fee NUMERIC(38,2) NOT NULL DEFAULT 0.00,
    PRIMARY KEY (day, status, from_account_id)
);

CREATE INDEX idx_daily_stats_lookup ON daily_transaction_stats (day, status, from_account_id);

CREATE OR REPLACE FUNCTION update_daily_transaction_stats()
RETURNS TRIGGER AS $$
BEGIN
    IF (TG_OP = 'INSERT') THEN
        INSERT INTO daily_transaction_stats (day, status, from_account_id, total_count, total_amount, total_fee)
        VALUES ((NEW.created_at AT TIME ZONE 'Asia/Bangkok')::date, NEW.status, NEW.from_account_id, 1, NEW.amount, NEW.fee_amount)
        ON CONFLICT (day, status, from_account_id) DO UPDATE
        SET total_count = daily_transaction_stats.total_count + 1,
            total_amount = daily_transaction_stats.total_amount + EXCLUDED.total_amount,
            total_fee = daily_transaction_stats.total_fee + EXCLUDED.total_fee;
    ELSIF (TG_OP = 'UPDATE') THEN
        IF (OLD.status IS DISTINCT FROM NEW.status) THEN
            -- Deduct from old status stats
            UPDATE daily_transaction_stats
            SET total_count = total_count - 1,
                total_amount = total_amount - OLD.amount,
                total_fee = total_fee - OLD.fee_amount
            WHERE day = (OLD.created_at AT TIME ZONE 'Asia/Bangkok')::date
              AND status = OLD.status
              AND from_account_id = OLD.from_account_id;
              
            -- Add to new status stats
            INSERT INTO daily_transaction_stats (day, status, from_account_id, total_count, total_amount, total_fee)
            VALUES ((NEW.created_at AT TIME ZONE 'Asia/Bangkok')::date, NEW.status, NEW.from_account_id, 1, NEW.amount, NEW.fee_amount)
            ON CONFLICT (day, status, from_account_id) DO UPDATE
            SET total_count = daily_transaction_stats.total_count + 1,
                total_amount = daily_transaction_stats.total_amount + EXCLUDED.total_amount,
                total_fee = daily_transaction_stats.total_fee + EXCLUDED.total_fee;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_update_daily_transaction_stats
AFTER INSERT OR UPDATE ON transfer_orders
FOR EACH ROW
EXECUTE FUNCTION update_daily_transaction_stats();

-- Seed initial data from transfer_orders
INSERT INTO daily_transaction_stats (day, status, from_account_id, total_count, total_amount, total_fee)
SELECT (created_at AT TIME ZONE 'Asia/Bangkok')::date AS day,
       status,
       from_account_id,
       COUNT(*) AS total_count,
       SUM(amount) AS total_amount,
       SUM(fee_amount) AS total_fee
FROM transfer_orders
GROUP BY 1, 2, 3
ON CONFLICT (day, status, from_account_id) DO UPDATE
SET total_count = daily_transaction_stats.total_count + EXCLUDED.total_count,
    total_amount = daily_transaction_stats.total_amount + EXCLUDED.total_amount,
    total_fee = daily_transaction_stats.total_fee + EXCLUDED.total_fee;
