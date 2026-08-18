CREATE TABLE sepay_payment_orders (
    id UUID PRIMARY KEY,
    order_code VARCHAR(32) NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    account_number VARCHAR(32) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    status VARCHAR(32) NOT NULL,
    viet_qr_url TEXT,
    sepay_transaction_id BIGINT,
    bank_brand_name VARCHAR(64),
    transfer_content VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_sepay_orders_code ON sepay_payment_orders(order_code);
CREATE INDEX idx_sepay_orders_user_id ON sepay_payment_orders(user_id);
CREATE INDEX idx_sepay_orders_status ON sepay_payment_orders(status);
CREATE UNIQUE INDEX uq_sepay_orders_tx_id ON sepay_payment_orders(sepay_transaction_id) WHERE sepay_transaction_id IS NOT NULL;

CREATE TABLE sepay_webhook_logs (
    id UUID PRIMARY KEY,
    sepay_transaction_id BIGINT UNIQUE,
    gateway VARCHAR(64),
    transaction_date VARCHAR(64),
    account_number VARCHAR(32),
    code VARCHAR(64),
    content TEXT,
    transfer_type VARCHAR(16),
    transfer_amount NUMERIC(19, 4),
    accumulated NUMERIC(19, 4),
    reference_code VARCHAR(128),
    processing_status VARCHAR(32) NOT NULL,
    raw_payload TEXT,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_sepay_webhooks_tx_id ON sepay_webhook_logs(sepay_transaction_id);
CREATE INDEX idx_sepay_webhooks_code ON sepay_webhook_logs(code);
