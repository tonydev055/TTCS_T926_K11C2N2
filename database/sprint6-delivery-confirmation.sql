BEGIN;
-- S6-08: xác nhận kết quả giao hàng và lưu đường dẫn chứng từ.
-- Có thể chạy lại an toàn, kể cả khi bảng đã được tạo trước đó.
CREATE TABLE IF NOT EXISTS delivery_confirmations (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    outcome VARCHAR(20) NOT NULL CHECK (outcome IN ('SUCCESS', 'FAILED')),
    recipient_name VARCHAR(150),
    delivered_at TIMESTAMP,
    proof_image_path TEXT,
    failure_reason TEXT,
    returned_to_warehouse BOOLEAN NOT NULL DEFAULT FALSE,
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT delivery_success_required_fields CHECK (
        outcome <> 'SUCCESS' OR (
            recipient_name IS NOT NULL AND LENGTH(TRIM(recipient_name)) > 0
            AND delivered_at IS NOT NULL
            AND proof_image_path IS NOT NULL AND LENGTH(TRIM(proof_image_path)) > 0
        )
    ),
    CONSTRAINT delivery_failure_required_reason CHECK (
        outcome <> 'FAILED' OR (failure_reason IS NOT NULL AND LENGTH(TRIM(failure_reason)) > 0)
    )
);

ALTER TABLE delivery_confirmations ADD COLUMN IF NOT EXISTS outcome VARCHAR(20);
ALTER TABLE delivery_confirmations ADD COLUMN IF NOT EXISTS recipient_name VARCHAR(150);
ALTER TABLE delivery_confirmations ADD COLUMN IF NOT EXISTS delivered_at TIMESTAMP;
ALTER TABLE delivery_confirmations ADD COLUMN IF NOT EXISTS proof_image_path TEXT;
ALTER TABLE delivery_confirmations ADD COLUMN IF NOT EXISTS failure_reason TEXT;
ALTER TABLE delivery_confirmations ADD COLUMN IF NOT EXISTS returned_to_warehouse BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE delivery_confirmations ADD COLUMN IF NOT EXISTS created_by BIGINT REFERENCES users(id);
ALTER TABLE delivery_confirmations ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_delivery_confirmations_order
    ON delivery_confirmations(order_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_delivery_confirmations_recent
    ON delivery_confirmations(created_at DESC);

DROP TRIGGER IF EXISTS delivery_confirmations_audit ON delivery_confirmations;
CREATE TRIGGER delivery_confirmations_audit
AFTER INSERT OR UPDATE ON delivery_confirmations
FOR EACH ROW EXECUTE FUNCTION sprint2_audit();
COMMIT;
