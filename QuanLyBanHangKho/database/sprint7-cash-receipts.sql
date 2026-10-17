-- S7-09: phiếu thu độc lập. Không sửa bảng orders/payments hiện có.
-- Chạy một lần trên database quanlybanhangkho sau các script nền tảng.
BEGIN;

CREATE TABLE IF NOT EXISTS cash_receipts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    receipt_code TEXT GENERATED ALWAYS AS ('PT-' || lpad(id::text, 8, '0')) STORED UNIQUE,
    customer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE RESTRICT,
    order_id BIGINT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    amount NUMERIC(18,2) NOT NULL CHECK (amount > 0),
    payment_method VARCHAR(20) NOT NULL CHECK (payment_method IN ('CASH', 'BANK_TRANSFER', 'OTHER')),
    reference_code VARCHAR(120),
    note TEXT,
    received_by BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS cash_receipts_customer_created_idx
    ON cash_receipts(customer_id, created_at DESC);
CREATE INDEX IF NOT EXISTS cash_receipts_order_idx
    ON cash_receipts(order_id) WHERE order_id IS NOT NULL;

COMMIT;
