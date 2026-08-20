-- V20260820183000: Open Banking ISO 20022 Messages & Payment Records

-- 1. ISO 20022 Inbound & Outbound Messages
CREATE TABLE IF NOT EXISTS iso_payment_messages (
    id UUID PRIMARY KEY,
    message_id VARCHAR(128) NOT NULL UNIQUE,
    client_id VARCHAR(64) NOT NULL,
    message_type VARCHAR(32) NOT NULL, -- PAIN_001, PAIN_002, CAMT_053
    direction VARCHAR(10) NOT NULL, -- INBOUND, OUTBOUND
    total_transactions INT NOT NULL DEFAULT 1,
    total_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    overall_status VARCHAR(20) NOT NULL, -- RECEIVED, PROCESSING, COMPLETED, PARTIALLY_COMPLETED, REJECTED
    raw_payload TEXT NOT NULL,
    signature_payload TEXT,
    signature_verified BOOLEAN NOT NULL DEFAULT FALSE,
    error_code VARCHAR(32),
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_iso_msg_client ON iso_payment_messages(client_id, created_at);
CREATE INDEX IF NOT EXISTS idx_iso_msg_status ON iso_payment_messages(overall_status);

-- 2. ISO Payment Records (Per-transaction item in batch)
CREATE TABLE IF NOT EXISTS iso_payment_records (
    id UUID PRIMARY KEY,
    message_id VARCHAR(128) NOT NULL REFERENCES iso_payment_messages(message_id) ON DELETE CASCADE,
    client_id VARCHAR(64) NOT NULL,
    instruction_id VARCHAR(128) NOT NULL,
    end_to_end_id VARCHAR(128) NOT NULL,
    transfer_order_id UUID,
    debtor_account VARCHAR(32) NOT NULL,
    creditor_account VARCHAR(32) NOT NULL,
    creditor_bank_code VARCHAR(32),
    amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    status VARCHAR(10) NOT NULL, -- ACCP, ACSP, ACSC, RJCT
    status_reason_code VARCHAR(32),
    status_reason_desc TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_iso_record_e2e UNIQUE (client_id, end_to_end_id)
);

CREATE INDEX IF NOT EXISTS idx_iso_records_status ON iso_payment_records(status);
CREATE INDEX IF NOT EXISTS idx_iso_records_msg ON iso_payment_records(message_id);
CREATE INDEX IF NOT EXISTS idx_iso_records_debtor ON iso_payment_records(debtor_account);
